package com.kilikili.web.ai.vision;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多模态视觉模型配置（OpenAI 兼容的 chat 接口）。
 * 与文本用的 DeepSeek 分离：DeepSeek 不支持图片输入，若要分析视频关键帧，
 * 需要单独配置 Qwen-VL / GPT-4o 等支持 image_url 的模型；未配置时封面自动降级为纯文本设计。
 */
@ConfigurationProperties(prefix = "kilikili.ai.vision")
public record VisionProperties(
        boolean enabled,
        String chatUrl,
        String apiKey,
        String model,
        long timeoutSeconds) {

    public long timeoutSeconds() {
        return timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }
}
