package com.kilikili.web.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 审核任务配置。API Key 与模型由 Spring AI 的 spring.ai.openai.* 提供。
 */
@ConfigurationProperties(prefix = "kilikili.ai")
public record AiReviewProperties(
        boolean enabled,
        int batchSize,
        long pollIntervalMs,
        boolean autoPublish) {
}
