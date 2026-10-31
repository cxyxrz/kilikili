package com.kilikili.web.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI ChatClient 的内容安全初审。api-key 未配置时 Spring AI 不会创建客户端，
 * 此时 available() 为 false，审核任务自动跳过。
 */
@Component
public class DeepSeekClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);
    private static final String SYSTEM_PROMPT = """
            你是中文视频网站的内容安全审核员，负责投稿的文本层面初审（标题、简介、创作声明、分区）。
            请判断是否包含以下风险：涉政敏感、色情低俗、暴力血腥、违法犯罪、危险行为示范、虚假医疗健康建议、广告导流（联系方式、二维码、站外引流）、侵犯他人隐私、危害未成年人。
            判定标准：
            - PASS：正常内容，无风险；
            - REVIEW：存在可疑或擦边表述，需要人工复核；
            - REJECT：明确违反上述任一红线。
            只输出 JSON，格式为 {"result":"PASS|REVIEW|REJECT","score":0-100,"reason":"30字以内的中文理由","tags":["命中的风险类型"]}，
            其中 score 表示风险分（越高越危险），无风险时 tags 为空数组。
            """;

    /** 封面设计提示词：只让模型产出文案与配色，图像由服务端渲染，保证结果可控可复现 */
    private static final String COVER_SYSTEM_PROMPT = """
            你是中文视频网站的封面设计师，负责根据投稿标题和简介设计封面文案与配色。
            要求：
            - headline：封面主文案，8-14 个汉字，提炼看点、有吸引力，不得标题党或夸大事实；
            - subline：副文案，12-20 个汉字，补充说明内容；
            - palette：3 个十六进制颜色，依次为背景起始色、背景结束色、强调色，要求 16:9 封面上文字可读、对比度足够；
            - motif：10 个字以内的视觉风格描述（如「科技感」「温暖治愈」「运动活力」）。
            只输出 JSON，格式为 {"headline":"主文案","subline":"副文案","palette":["#112233","#445566","#ffcc00"],"motif":"风格"}。
            """;

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    /** 弹幕审核专用提示词：弹幕短、口语化，只关注明显违规与广告导流 */
    private static final String DANMAKU_SYSTEM_PROMPT = """
            你是中文视频网站的弹幕审核员，负责对单条弹幕做实时内容安全判断。
            弹幕通常很短、口语化、带网络用语，请重点识别：涉政敏感、色情低俗、暴力血腥、违法犯罪、辱骂攻击、广告导流（联系方式、二维码、站外引流）、危害未成年人。
            判定标准：
            - PASS：正常吐槽/互动/玩梗，无风险；
            - REVIEW：擦边或语义可疑，但不足以直接拒绝；
            - REJECT：明确违反上述红线或明显广告导流。
            只输出 JSON，格式为 {"result":"PASS|REVIEW|REJECT","score":0-100,"reason":"20字以内的中文理由","tags":["命中的风险类型"]}。
            """;

    public DeepSeekClient(ObjectMapper objectMapper, ObjectProvider<ChatClient.Builder> chatClientBuilder) {
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
        this.chatClient = builder == null ? null : builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public boolean available() {
        return chatClient != null;
    }

    public Verdict review(AiReviewSource source) {
        if (chatClient == null) throw new IllegalStateException("未配置 DEEPSEEK_API_KEY");
        String content;
        try {
            content = chatClient.prompt().user(userPrompt(source)).call().content();
        } catch (Exception e) {
            throw new IllegalStateException("调用 DeepSeek 失败：" + e.getMessage(), e);
        }
        if (content == null || content.isBlank()) throw new IllegalStateException("DeepSeek 返回内容为空");
        return parse(content);
    }

    /** 单条弹幕的实时安全判断，失败由调用方决定是否放行 */
    public Verdict moderateDanmaku(String danmaku) {
        if (chatClient == null) throw new IllegalStateException("未配置 DEEPSEEK_API_KEY");
        String reply;
        try {
            reply = chatClient.prompt()
                    .system(DANMAKU_SYSTEM_PROMPT)
                    .user("弹幕内容：" + (danmaku == null ? "" : danmaku))
                    .call()
                    .content();
        } catch (Exception e) {
            throw new IllegalStateException("调用 DeepSeek 失败：" + e.getMessage(), e);
        }
        if (reply == null || reply.isBlank()) throw new IllegalStateException("DeepSeek 返回内容为空");
        return parse(reply);
    }

    /** 生成封面设计稿（文案 + 配色），失败抛出由调用方降级到本地规则 */
    public CoverBrief designCover(String title, String description, String categoryName) {
        if (chatClient == null) throw new IllegalStateException("未配置 DEEPSEEK_API_KEY");
        String reply;
        try {
            reply = chatClient.prompt()
                    .system(COVER_SYSTEM_PROMPT)
                    .user("""
                            视频标题：%s
                            视频简介：%s
                            所在分区：%s
                            """.formatted(nullToEmpty(title), nullToEmpty(description), nullToEmpty(categoryName)))
                    .call()
                    .content();
        } catch (Exception e) {
            throw new IllegalStateException("调用 DeepSeek 失败：" + e.getMessage(), e);
        }
        if (reply == null || reply.isBlank()) throw new IllegalStateException("DeepSeek 返回内容为空");
        try {
            JsonNode node = objectMapper.readTree(stripCodeFence(reply));
            String headline = node.path("headline").asText("").trim();
            String subline = node.path("subline").asText("").trim();
            String motif = node.path("motif").asText("").trim();
            java.util.List<String> palette = new java.util.ArrayList<>();
            for (JsonNode color : node.path("palette")) {
                String value = color.asText("").trim();
                if (value.matches("#[0-9a-fA-F]{6}")) palette.add(value);
            }
            if (headline.isEmpty() || palette.size() < 2) throw new IllegalStateException("封面设计字段缺失");
            return new CoverBrief(truncate(headline, 18), truncate(subline, 30), palette, truncate(motif, 12));
        } catch (Exception e) {
            log.warn("DeepSeek 封面设计结果无法解析：{}", truncate(reply, 300));
            throw new IllegalStateException("封面设计结果解析失败", e);
        }
    }

    private String userPrompt(AiReviewSource source) {
        return """
                请审核以下投稿：
                视频标题：%s
                视频简介：%s
                创作声明：%s
                分区 ID：%d
                投稿人：%s
                """.formatted(
                nullToEmpty(source.title()),
                nullToEmpty(source.description()),
                nullToEmpty(source.creationDeclaration()),
                source.categoryId(),
                nullToEmpty(source.authorName()));
    }

    private Verdict parse(String content) {
        try {
            JsonNode node = objectMapper.readTree(stripCodeFence(content));
            String result = node.path("result").asText("REVIEW").trim().toUpperCase();
            if (!result.equals("PASS") && !result.equals("REVIEW") && !result.equals("REJECT")) result = "REVIEW";
            int score = Math.max(0, Math.min(100, node.path("score").asInt(result.equals("PASS") ? 10 : 60)));
            String reason = node.path("reason").asText("").trim();
            JsonNode tagsNode = node.path("tags");
            StringBuilder tags = new StringBuilder();
            if (tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    String text = tag.asText("").trim();
                    if (!text.isEmpty()) tags.append(tags.isEmpty() ? "" : "、").append(text);
                }
            }
            if (tags.length() > 0) reason = reason.isEmpty() ? tags.toString() : reason + "（" + tags + "）";
            return new Verdict(result, score, truncate(reason, 480), truncate(content, 2000));
        } catch (Exception e) {
            log.warn("DeepSeek 返回内容无法解析为 JSON：{}", truncate(content, 500));
            throw new IllegalStateException("AI 审核结果解析失败", e);
        }
    }

    /** 模型偶尔会用 ```json 包裹，这里去掉围栏再解析 */
    private String stripCodeFence(String content) {
        String text = content.trim();
        if (!text.startsWith("```")) return text;
        int firstLine = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstLine < 0 || lastFence <= firstLine) return text;
        return text.substring(firstLine + 1, lastFence).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public record Verdict(String result, int score, String reason, String detail) {
    }

    /** 封面设计稿：headline 主文案、subline 副文案、palette 配色、motif 风格 */
    public record CoverBrief(String headline, String subline, java.util.List<String> palette, String motif) {
    }
}
