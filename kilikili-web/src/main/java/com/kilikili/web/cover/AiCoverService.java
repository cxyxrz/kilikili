package com.kilikili.web.cover;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.ai.DeepSeekClient;
import com.kilikili.web.ai.vision.VisionClient;
import com.kilikili.web.entity.Video;
import com.kilikili.web.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI 封面生成：优先把视频关键帧交给多模态模型（结合画面主体/色调设计），
 * 其次由 DeepSeek 依据标题文本设计，最后降级为本地规则；文案与配色确定后由服务端 Java2D 渲染 16:9 封面。
 */
@Service
public class AiCoverService {
    private static final Logger log = LoggerFactory.getLogger(AiCoverService.class);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int VARIANTS = 3;
    /** 依次尝试的中文字体，取系统里第一个可用的，避免中文渲染成方块 */
    private static final String[] FONT_FAMILIES = {
            "PingFang SC", "Hiragino Sans GB", "Heiti SC", "Noto Sans CJK SC",
            "Source Han Sans CN", "Microsoft YaHei", "Songti SC"};
    private static final List<String[]> FALLBACK_PALETTES = List.of(
            new String[]{"#131A3A", "#4553D6", "#FFC857"},
            new String[]{"#0E2430", "#2E6F88", "#6FE3D2"},
            new String[]{"#2A1444", "#B4558A", "#FFB784"},
            new String[]{"#102A20", "#2E8B57", "#F0E39A"});

    private final DeepSeekClient deepSeekClient;
    private final VisionClient visionClient;
    private final VideoFrameExtractor frameExtractor;
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final String fontFamily;

    public AiCoverService(DeepSeekClient deepSeekClient, VisionClient visionClient, VideoFrameExtractor frameExtractor,
                          VideoMapper videoMapper, StorageService storageService) {
        this.deepSeekClient = deepSeekClient;
        this.visionClient = visionClient;
        this.frameExtractor = frameExtractor;
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.fontFamily = resolveFontFamily();
    }

    /** 对已上传的视频截帧后生成封面：用于页面已提交视频、但想基于真实画面重新生成封面 */
    public Map<String, Object> generateForVideo(long userId, long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) throw new BusinessException("视频不存在");
        if (video.authorId() == null || video.authorId() != userId) throw new BusinessException("只能为自己的视频生成封面");
        List<byte[]> frames;
        try {
            frames = frameExtractor.extract(video.objectKey(), 3);
        } catch (Exception exception) {
            throw new BusinessException("视频截帧失败：" + exception.getMessage());
        }
        if (frames.isEmpty()) throw new BusinessException("未能从视频中截取到画面，请上传常规格式的 MP4 视频");
        return generate(userId, video.title(), video.description(), null, frames);
    }

    /**
     * @param frames 视频关键帧（JPEG 字节），非空且视觉模型可用时优先基于画面设计
     */
    public Map<String, Object> generate(long userId, String title, String description, String categoryName,
                                        List<byte[]> frames) {
        if (title == null || title.isBlank()) throw new BusinessException("请先填写视频标题，再生成封面");
        DeepSeekClient.CoverBrief brief = null;
        String scene = null;
        boolean visionUsed = false;
        int frameCount = frames == null ? 0 : frames.size();

        if (frameCount > 0 && visionClient.available()) {
            try {
                VisionClient.VisionResult result = visionClient.designCover(title, description, categoryName, frames);
                brief = result.brief();
                scene = result.scene();
                visionUsed = true;
                log.info("封面已结合画面生成: userId={}, frames={}, scene={}, headline={}", userId, frameCount, scene, brief.headline());
            } catch (Exception exception) {
                log.warn("视觉模型分析失败，降级为文本设计: {}", exception.getMessage());
            }
        }

        boolean aiGenerated;
        if (brief != null) {
            aiGenerated = true;
        } else {
            try {
                brief = deepSeekClient.designCover(title, description, categoryName);
                aiGenerated = true;
                log.info("AI 封面设计完成: userId={}, headline={}, motif={}", userId, brief.headline(), brief.motif());
            } catch (Exception exception) {
                brief = fallbackBrief(title, description);
                aiGenerated = false;
                log.warn("AI 封面设计降级为本地规则: title={}, reason={}", title, exception.getMessage());
            }
        }

        List<Map<String, Object>> candidates = new ArrayList<>(VARIANTS);
        for (int variant = 0; variant < VARIANTS; variant++) {
            try {
                byte[] png = render(brief, variant);
                String objectKey = "covers/" + userId + "/ai-" + UUID.randomUUID() + "-" + variant + ".png";
                Path temp = Files.createTempFile("kilikili-ai-cover-", ".png");
                try {
                    Files.write(temp, png);
                    storageService.uploadLocalFile(objectKey, temp, "image/png");
                } finally {
                    Files.deleteIfExists(temp);
                }
                candidates.add(Map.of(
                        "objectKey", objectKey,
                        "url", storageService.presignedReadUrl(objectKey),
                        "variant", variant));
            } catch (IOException exception) {
                throw new IllegalStateException("AI 封面渲染失败: " + exception.getMessage(), exception);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiGenerated", aiGenerated);
        result.put("visionUsed", visionUsed);
        result.put("frameCount", frameCount);
        result.put("scene", scene == null ? "" : scene);
        result.put("design", Map.of(
                "headline", brief.headline(),
                "subline", brief.subline(),
                "motif", brief.motif(),
                "palette", brief.palette()));
        result.put("candidates", candidates);
        return result;
    }

    /** DeepSeek 不可用时的兜底设计稿：标题截取 + 稳定配色，保证演示与降级可用 */
    private DeepSeekClient.CoverBrief fallbackBrief(String title, String description) {
        String headline = title.strip();
        if (headline.length() > 14) headline = headline.substring(0, 14);
        String subline = description == null ? "" : description.strip();
        if (subline.length() > 24) subline = subline.substring(0, 24);
        if (subline.isEmpty()) subline = "在 KiliKili 发现更多精彩内容";
        String[] palette = FALLBACK_PALETTES.get(Math.floorMod(title.hashCode(), FALLBACK_PALETTES.size()));
        return new DeepSeekClient.CoverBrief(headline, subline, Arrays.asList(palette), "精选推荐");
    }

    /** 按 variant 生成三种配色/版式不同的封面 */
    private byte[] render(DeepSeekClient.CoverBrief brief, int variant) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Color start = hex(brief.palette().get(0));
            Color end = hex(brief.palette().get(1));
            Color accent = brief.palette().size() > 2 ? hex(brief.palette().get(2)) : brighten(start, 0.55f);
            if (variant == 1) {
                Color swap = start;
                start = end;
                end = swap;
            } else if (variant == 2) {
                start = darken(accent, 0.62f);
                end = darken(start, 0.55f);
            }

            g.setPaint(new GradientPaint(0, 0, start, WIDTH, HEIGHT, end));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            paintDecor(g, accent, variant);

            int margin = 96;
            Font headlineFont = fitFont(g, brief.headline(), WIDTH - margin * 2, 88, 52);
            FontMetrics metrics = g.getFontMetrics(headlineFont);
            List<String> lines = wrap(brief.headline(), metrics, WIDTH - margin * 2, 2);
            int lineHeight = metrics.getHeight();
            int blockHeight = lines.size() * lineHeight;
            int baseY = (HEIGHT - blockHeight) / 2 + (variant == 1 ? 20 : 10);
            g.setFont(headlineFont);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int x = variant == 1 ? (WIDTH - metrics.stringWidth(line)) / 2 : margin;
                drawShadowText(g, line, x, baseY + i * lineHeight, Color.WHITE);
            }

            Font subFont = new Font(fontFamily, Font.PLAIN, 30);
            g.setFont(subFont);
            FontMetrics subMetrics = g.getFontMetrics(subFont);
            String subline = brief.subline() == null ? "" : brief.subline();
            if (!subline.isBlank()) {
                int subBaseY = baseY + blockHeight + 22;
                int x = variant == 1 ? (WIDTH - subMetrics.stringWidth(subline)) / 2 : margin;
                drawShadowText(g, subline, x, subBaseY, new Color(255, 255, 255, 210));
            }

            // 强调色短横线放在标题块上方，避免压字
            int ruleWidth = 72;
            int ruleY = baseY - metrics.getAscent() - 30;
            int ruleX = variant == 1 ? (WIDTH - ruleWidth) / 2 : margin;
            g.setColor(accent);
            g.fillRoundRect(ruleX, ruleY, ruleWidth, 8, 8, 8);

            // 站点标识与风格标签
            Font brandFont = new Font(fontFamily, Font.BOLD, 26);
            g.setFont(brandFont);
            g.setColor(new Color(255, 255, 255, 190));
            g.drawString("KiliKili", margin, HEIGHT - 56);
            String motif = brief.motif() == null ? "" : brief.motif();
            if (!motif.isBlank()) {
                Font tagFont = new Font(fontFamily, Font.PLAIN, 22);
                g.setFont(tagFont);
                int tagWidth = g.getFontMetrics(tagFont).stringWidth(motif) + 36;
                int tagX = WIDTH - margin - tagWidth;
                int tagY = HEIGHT - 92;
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                g.fill(new RoundRectangle2D.Float(tagX, tagY, tagWidth, 44, 22, 22));
                g.setColor(Color.WHITE);
                g.drawString(motif, tagX + 18, tagY + 30);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** 三种版式不同的装饰图形，避免三张候选看起来一模一样 */
    private void paintDecor(Graphics2D g, Color accent, int variant) {
        Color soft = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70);
        if (variant == 0) {
            g.setColor(soft);
            g.fill(new Ellipse2D.Float(WIDTH - 420, -140, 620, 620));
            g.setColor(new Color(255, 255, 255, 38));
            g.fill(new Ellipse2D.Float(WIDTH - 300, 240, 300, 300));
        } else if (variant == 1) {
            // 左上柔光 + 右下强调圆，避免圆角矩形边缘形成生硬的色带
            g.setColor(new Color(255, 255, 255, 22));
            g.fill(new Ellipse2D.Float(-280, -280, 640, 640));
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 95));
            g.fill(new Ellipse2D.Float(WIDTH - 420, HEIGHT - 300, 620, 620));
        } else {
            g.setColor(new Color(255, 255, 255, 26));
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 8; col++) {
                    g.fillRoundRect(WIDTH - 400 + col * 44, 96 + row * 44, 26, 26, 6, 6);
                }
            }
            g.setColor(soft);
            g.fill(new Ellipse2D.Float(WIDTH - 260, HEIGHT - 260, 420, 420));
        }
        g.setColor(new Color(255, 255, 255, 22));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(0, HEIGHT - 170, WIDTH, HEIGHT - 240);
    }

    /** 字号自适应：优先单行放下，放不下再从 76 递减到 2 行可容纳的最小字号 */
    private Font fitFont(Graphics2D g, String text, int maxWidth, int maxSize, int minSize) {
        for (int size = maxSize; size >= minSize; size -= 4) {
            Font font = new Font(fontFamily, Font.BOLD, size);
            if (g.getFontMetrics(font).stringWidth(text) <= maxWidth) return font;
        }
        for (int size = 76; size >= minSize; size -= 4) {
            Font font = new Font(fontFamily, Font.BOLD, size);
            FontMetrics metrics = g.getFontMetrics(font);
            if (countLines(text, metrics, maxWidth) <= 2) return font;
        }
        return new Font(fontFamily, Font.BOLD, minSize);
    }

    /** 计算实际需要的行数（不做截断），用于判断字号是否合适 */
    private int countLines(String text, FontMetrics metrics, int maxWidth) {
        int lines = 1;
        StringBuilder current = new StringBuilder();
        for (char ch : text.toCharArray()) {
            current.append(ch);
            if (metrics.stringWidth(current.toString()) > maxWidth) {
                current.deleteCharAt(current.length() - 1);
                lines++;
                current = new StringBuilder().append(ch);
            }
        }
        return lines;
    }

    private List<String> wrap(String text, FontMetrics metrics, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        StringBuilder current = new StringBuilder();
        for (char ch : text.toCharArray()) {
            current.append(ch);
            if (metrics.stringWidth(current.toString()) > maxWidth) {
                current.deleteCharAt(current.length() - 1);
                if (lines.size() == maxLines - 1) {
                    // 最后一行放不下时用省略号收尾
                    String last = current + "…";
                    while (metrics.stringWidth(last) > maxWidth && current.length() > 0) {
                        current.deleteCharAt(current.length() - 1);
                        last = current + "…";
                    }
                    lines.add(last);
                    return lines;
                }
                lines.add(current.toString());
                current = new StringBuilder().append(ch);
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) lines.add(current.toString());
        return lines;
    }

    private void drawShadowText(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(text, x + 3, y + 3);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private Color hex(String value) {
        try {
            return new Color(Integer.parseInt(value.substring(1), 16));
        } catch (Exception exception) {
            return new Color(0x2B, 0x36, 0x5A);
        }
    }

    private Color darken(Color color, float factor) {
        return new Color(
                Math.round(color.getRed() * factor),
                Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor));
    }

    private Color brighten(Color color, float factor) {
        return new Color(
                Math.min(255, Math.round(color.getRed() * factor + 255 * (1 - factor))),
                Math.min(255, Math.round(color.getGreen() * factor + 255 * (1 - factor))),
                Math.min(255, Math.round(color.getBlue() * factor + 255 * (1 - factor))));
    }

    private static String resolveFontFamily() {
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.CHINA);
        for (String candidate : FONT_FAMILIES) {
            for (String name : available) {
                if (name.equalsIgnoreCase(candidate)) return name;
            }
        }
        return Font.SANS_SERIF;
    }
}
