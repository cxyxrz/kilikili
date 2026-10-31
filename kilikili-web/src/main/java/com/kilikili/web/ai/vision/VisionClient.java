package com.kilikili.web.ai.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.web.ai.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模态模型调用：把视频关键帧以 data URL 形式发给 OpenAI 兼容的视觉接口，
 * 让模型结合画面主体、场景与色调产出封面设计稿（文案 + 配色）。
 */
@Component
public class VisionClient {
    private static final Logger log = LoggerFactory.getLogger(VisionClient.class);

    private static final String SYSTEM_PROMPT = """
            你是中文视频网站的封面设计师，会收到投稿标题、简介、分区，以及从视频里截取的若干关键帧。
            请先观察画面（主体人物/物体、场景、构图、主色调），再设计封面：
            - headline：封面主文案，8-14 个汉字，提炼画面与标题的共同看点，不得标题党或夸大事实；
            - subline：副文案，12-20 个汉字，补充说明内容；
            - palette：3 个十六进制颜色，依次为背景起始色、背景结束色、强调色，优先从画面主色中提取，并保证 16:9 封面上文字对比度足够；
            - motif：10 个字以内的视觉风格描述；
            - scene：15 个字以内概括画面主要内容。
            只输出 JSON，格式为 {"headline":"主文案","subline":"副文案","palette":["#112233","#445566","#ffcc00"],"motif":"风格","scene":"画面概括"}。
            """;

    private final VisionProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public VisionClient(VisionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) (properties.timeoutSeconds() * 1000);
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean available() {
        return properties.enabled()
                && notBlank(properties.chatUrl())
                && notBlank(properties.apiKey())
                && notBlank(properties.model());
    }

    /** 结合关键帧生成封面设计稿；失败抛异常由调用方降级为文本设计 */
    public VisionResult designCover(String title, String description, String categoryName, List<byte[]> frames) {
        if (!available()) throw new IllegalStateException("未配置多模态视觉模型");
        if (frames == null || frames.isEmpty()) throw new IllegalStateException("没有可分析的视频画面");

        List<Map<String, Object>> content = new ArrayList<>(frames.size() + 1);
        content.add(Map.of("type", "text", "text", userPrompt(title, description, categoryName, frames.size())));
        for (byte[] frame : frames) {
            String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(frame);
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("temperature", 0);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", content)));

        String reply;
        try {
            reply = restClient.post()
                    .uri(properties.chatUrl())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception exception) {
            throw new IllegalStateException("视觉模型调用失败：" + exception.getMessage(), exception);
        }
        if (reply == null || reply.isBlank()) throw new IllegalStateException("视觉模型返回内容为空");
        log.info("视觉模型原始返回: {}", reply.length() > 300 ? reply.substring(0, 300) : reply);
        return parse(reply);
    }

    private String userPrompt(String title, String description, String categoryName, int frameCount) {
        return "投稿标题：" + (title == null ? "" : title)
                + "\n投稿简介：" + (description == null ? "" : description)
                + "\n所在分区：" + (categoryName == null || categoryName.isBlank() ? "未指定" : categoryName)
                + "\n视频关键帧：共 " + frameCount + " 张，按时间顺序排列。";
    }

    private VisionResult parse(String reply) {
        String json = stripCodeFence(reply);
        try {
            JsonNode node = objectMapper.readTree(json);
            // 兼容直接返回设计稿 JSON 与 OpenAI 标准响应（设计稿在 choices[0].message.content 内）
            JsonNode contentNode = node.path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode()) {
                String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
                node = objectMapper.readTree(stripCodeFence(content));
            }
            String headline = node.path("headline").asText("").strip();
            String subline = node.path("subline").asText("").strip();
            String motif = node.path("motif").asText("").strip();
            String scene = node.path("scene").asText("").strip();
            List<String> palette = new ArrayList<>(3);
            for (JsonNode color : node.path("palette")) {
                String value = color.asText("").strip();
                if (!value.matches("#[0-9a-fA-F]{6}")) continue;
                palette.add(value);
                if (palette.size() == 3) break;
            }
            if (headline.isEmpty() || palette.size() < 2) throw new IllegalStateException("设计稿字段缺失");
            return new VisionResult(
                    new DeepSeekClient.CoverBrief(headline, subline, palette, motif.isEmpty() ? "智能设计" : motif),
                    scene);
        } catch (Exception exception) {
            throw new IllegalStateException("视觉模型返回格式无法解析：" + exception.getMessage(), exception);
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine > 0 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).strip();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 设计稿 + 模型对画面的概括 */
    public record VisionResult(DeepSeekClient.CoverBrief brief, String scene) {
    }
}
