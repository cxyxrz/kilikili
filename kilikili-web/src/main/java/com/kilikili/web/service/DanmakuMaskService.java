package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.DanmakuMaskAnalysis;
import com.kilikili.web.entity.DanmakuMaskSegment;
import com.kilikili.web.entity.Video;
import com.kilikili.web.mapper.DanmakuInsightMapper;
import com.kilikili.web.mapper.VideoMapper;
import com.kilikili.web.transcode.TranscodeProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 智能弹幕防挡脸：视频首次请求遮罩时懒触发，后台用 FFmpeg 按 1fps 抽帧，
 * 再用 Java ImageIO 做肤色区域检测，把画面中上部的人脸区域按时间段落库。
 * 前端弹幕渲染时读取这些片段，把弹幕轨道限制在人脸区域之外。
 */
@Service
public class DanmakuMaskService {
    private static final Logger log = LoggerFactory.getLogger(DanmakuMaskService.class);
    /** 单个视频最多分析前 10 分钟，避免长视频抽帧过久 */
    private static final int MAX_ANALYZE_SECONDS = 600;
    private static final int FRAME_WIDTH = 256;
    private static final int MAX_SEGMENT_HEIGHT_RATIO = 55;

    private final DanmakuInsightMapper insightMapper;
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final TranscodeProperties transcodeProperties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    /** 当前进程内正在分析的视频，避免重复提交；服务重启后该集合为空，历史 RUNNING 会自动重跑 */
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final Path workDir = Path.of("/tmp/kilikili-danmaku-mask");

    public DanmakuMaskService(DanmakuInsightMapper insightMapper, VideoMapper videoMapper,
                              StorageService storageService, TranscodeProperties transcodeProperties) {
        this.insightMapper = insightMapper;
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.transcodeProperties = transcodeProperties;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /** 查询视频的人脸遮罩片段，首次查询会触发后台分析并立即返回 RUNNING */
    public Map<String, Object> mask(long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) throw new BusinessException("视频不存在");
        ensureAnalyzed(video);
        DanmakuMaskAnalysis analysis = insightMapper.findAnalysis(videoId);
        List<DanmakuMaskSegment> segments = insightMapper.listSegments(videoId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", analysis == null ? "NONE" : analysis.status());
        result.put("frameCount", analysis == null || analysis.frameCount() == null ? 0 : analysis.frameCount());
        result.put("message", analysis == null ? null : analysis.message());
        result.put("segments", segments);
        return result;
    }

    private void ensureAnalyzed(Video video) {
        DanmakuMaskAnalysis analysis = insightMapper.findAnalysis(video.id());
        if (analysis != null && ("READY".equals(analysis.status()) || "FAILED".equals(analysis.status()))) return;
        // 正在本进程分析则直接返回；RUNNING 但集合中没有任务，说明是重启前遗留状态，允许重跑
        if (!running.add(video.id())) return;
        try {
            insightMapper.deleteSegments(video.id());
            insightMapper.upsertAnalysis(video.id(), "RUNNING", 0, null);
            executor.submit(() -> analyze(video));
            log.info("弹幕防挡脸分析任务已提交: videoId={}", video.id());
        } catch (RuntimeException exception) {
            // 提交任务本身失败时释放占位，下次请求可重试
            running.remove(video.id());
            throw exception;
        }
    }

    private void analyze(Video video) {
        long videoId = video.id();
        long startedAt = System.currentTimeMillis();
        Path dir = null;
        try {
            Files.createDirectories(workDir);
            dir = Files.createTempDirectory(workDir, "video-" + videoId + "-");
            String objectKey = video.objectKey();
            String suffix = objectKey.contains(".") ? objectKey.substring(objectKey.lastIndexOf('.')) : ".mp4";
            Path sourceFile = dir.resolve("source" + suffix);
            // 原片先落到本地再挂载进容器，避免容器内无法访问 MinIO 的 presigned 地址
            storageService.downloadObject(objectKey, sourceFile);
            extractFrames(dir, sourceFile.getFileName().toString());

            List<MaskRun> runs = detectRuns(dir);
            insightMapper.deleteSegments(videoId);
            for (MaskRun run : runs) {
                insightMapper.insertSegment(videoId, run.startMs(), run.endMs(), run.top(), run.bottom(), run.faceRatio());
            }
            int frameCount = countFrames(dir);
            insightMapper.upsertAnalysis(videoId, "READY", frameCount, null);
            log.info("弹幕防挡脸分析完成: videoId={}, frames={}, segments={}, 耗时={}ms",
                    videoId, frameCount, runs.size(), System.currentTimeMillis() - startedAt);
        } catch (Exception exception) {
            String message = truncate(friendlyMessage(exception), 480);
            insightMapper.upsertAnalysis(videoId, "FAILED", 0, message);
            log.warn("弹幕防挡脸分析失败: videoId={}, error={}", videoId, message);
        } finally {
            running.remove(videoId);
            deleteRecursively(dir);
        }
    }

    private void extractFrames(Path dir, String sourceName) throws Exception {
        List<String> command = new ArrayList<>();
        boolean docker = transcodeProperties.useDocker();
        if (docker) {
            command.add("docker");
            command.addAll(List.of("run", "--rm", "-v", dir.toAbsolutePath() + ":/work",
                    "--entrypoint", "/ffmpeg", transcodeProperties.dockerImage()));
        } else {
            command.add(transcodeProperties.ffmpegPath());
        }
        String outPattern = docker ? "/work/f%05d.jpg" : dir.resolve("f%05d.jpg").toAbsolutePath().toString();
        command.addAll(List.of(
                "-y", "-nostats", "-loglevel", "error",
                "-t", String.valueOf(MAX_ANALYZE_SECONDS),
                "-i", docker ? "/work/" + sourceName : sourceName,
                "-vf", "fps=1,scale=" + FRAME_WIDTH + ":-2",
                "-q:v", "3",
                outPattern));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(20, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("抽帧超时");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("抽帧失败，退出码 " + process.exitValue() + "：" + truncate(output, 400));
        }
    }

    /** 逐帧检测肤色区域，把相邻帧中相互重叠的人脸区域合并成一个时间段 */
    private List<MaskRun> detectRuns(Path dir) throws Exception {
        List<Path> frames;
        try (Stream<Path> paths = Files.list(dir)) {
            frames = paths.filter(path -> path.getFileName().toString().endsWith(".jpg"))
                    .sorted()
                    .toList();
        }
        List<MaskRun> runs = new ArrayList<>();
        MaskRun current = null;
        for (int index = 0; index < frames.size(); index++) {
            BufferedImage image;
            try {
                image = ImageIO.read(frames.get(index).toFile());
            } catch (Exception exception) {
                image = null;
            }
            MaskCandidate candidate = image == null ? null : detect(image);
            if (candidate == null) {
                if (current != null) {
                    runs.add(current.withEnd(index * 1000));
                    current = null;
                }
                continue;
            }
            if (current == null) {
                current = new MaskRun(index * 1000, (index + 1) * 1000,
                        candidate.top(), candidate.bottom(), candidate.faceRatio());
            } else {
                current = new MaskRun(current.startMs(), (index + 1) * 1000,
                        Math.min(current.top(), candidate.top()),
                        Math.max(current.bottom(), candidate.bottom()),
                        Math.max(current.faceRatio(), candidate.faceRatio()));
            }
        }
        if (current != null) runs.add(current.withEnd(frames.size() * 1000));
        return runs;
    }

    /**
     * 在画面中上部按行统计肤色像素密度，取密度最高的一段连续行作为人脸区域。
     * 返回值均为相对画面高度的 0~1 比例。
     */
    private MaskCandidate detect(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 32 || height < 32) return null;
        int maxRow = (int) (height * 0.80);
        int startX = (int) (width * 0.10);
        int endX = (int) (width * 0.90);
        int stepX = Math.max(1, width / 160);
        int sampled = Math.max(1, (endX - startX) / stepX);
        int rowThreshold = Math.max(2, (int) Math.round(sampled * 0.16));

        int[] rowSkin = new int[maxRow];
        for (int y = 0; y < maxRow; y++) {
            int count = 0;
            for (int x = startX; x < endX; x += stepX) {
                int rgb = image.getRGB(x, y);
                if (isSkinTone((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)) count++;
            }
            rowSkin[y] = count;
        }

        int bestStart = -1;
        int bestEnd = -1;
        int bestLength = 0;
        int currentStart = -1;
        int lastHit = -1;
        // 允许 2 行间隙，避免眼睛/头发等非肤色行把区域切断
        for (int y = 0; y < maxRow; y++) {
            if (rowSkin[y] >= rowThreshold) {
                if (currentStart < 0) currentStart = y;
                lastHit = y;
            } else if (currentStart >= 0 && y - lastHit > 2) {
                if (lastHit - currentStart + 1 > bestLength) {
                    bestLength = lastHit - currentStart + 1;
                    bestStart = currentStart;
                    bestEnd = lastHit;
                }
                currentStart = -1;
            }
        }
        if (currentStart >= 0 && lastHit - currentStart + 1 > bestLength) {
            bestLength = lastHit - currentStart + 1;
            bestStart = currentStart;
            bestEnd = lastHit;
        }
        if (bestStart < 0 || bestLength < height * 0.05) return null;

        double top = (double) bestStart / height;
        double bottom = (double) (bestEnd + 1) / height;
        // 肤色区域过半屏通常是背景（木色/暖色墙面），不可信
        if ((bottom - top) * 100 > MAX_SEGMENT_HEIGHT_RATIO) return null;

        long skinPixels = 0;
        for (int y = bestStart; y <= bestEnd; y++) skinPixels += rowSkin[y];
        double ratio = skinPixels / ((double) bestLength * sampled);
        if (ratio < 0.22) return null;
        return new MaskCandidate(round(top), round(bottom), round(ratio));
    }

    private static boolean isSkinTone(int r, int g, int b) {
        int y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        int cb = (int) (-0.168736 * r - 0.331264 * g + 0.5 * b + 128);
        int cr = (int) (0.5 * r - 0.418688 * g - 0.081312 * b + 128);
        return y >= 60 && y <= 240 && cb >= 77 && cb <= 130 && cr >= 133 && cr <= 175 && r > 60 && r > b;
    }

    private int countFrames(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            return (int) paths.filter(path -> path.getFileName().toString().endsWith(".jpg")).count();
        } catch (Exception exception) {
            return 0;
        }
    }

    private String friendlyMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        if (transcodeProperties.useDocker() && message.contains("Cannot run program \"docker\"")) {
            return "无法启动 docker 命令，请确认本机已安装 Docker 并正在运行";
        }
        return message;
    }

    private void deleteRecursively(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record MaskCandidate(double top, double bottom, double faceRatio) {
    }

    private record MaskRun(int startMs, int endMs, double top, double bottom, double faceRatio) {
        MaskRun withEnd(int end) {
            return new MaskRun(startMs, end, top, bottom, faceRatio);
        }
    }
}
