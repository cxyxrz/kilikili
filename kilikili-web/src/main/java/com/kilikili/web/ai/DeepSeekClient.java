package com.kilikili.web.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DeepSeek Chat Completions 客户端，对视频文本信息做内容安全初审。
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

    private final AiReviewProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekClient(AiReviewProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Verdict review(AiReviewSource source) {
        String content;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.model());
            payload.put("temperature", 0);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", new Object[]{
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userPrompt(source))
            });
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.deepseekApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("DeepSeek 返回状态码 " + response.statusCode() + "：" + abbreviate(response.body()));
            }
            content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) throw new IllegalStateException("DeepSeek 返回内容为空");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 审核被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用 DeepSeek 失败：" + e.getMessage(), e);
        }
        return parse(content);
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
            log.warn("DeepSeek 返回内容无法解析为 JSON：{}", abbreviate(content));
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

    private String trimBaseUrl() {
        String base = properties.baseUrl() == null || properties.baseUrl().isBlank()
                ? "https://api.deepseek.com" : properties.baseUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String abbreviate(String value) {
        return truncate(value, 500);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public record Verdict(String result, int score, String reason, String detail) {
    }
}
