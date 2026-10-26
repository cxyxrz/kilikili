package com.kilikili.web.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek AI 审核配置。api-key 为空时审核任务自动跳过，不会写入审核记录。
 */
@ConfigurationProperties(prefix = "kilikili.ai")
public record AiReviewProperties(
        boolean enabled,
        String deepseekApiKey,
        String baseUrl,
        String model,
        int timeoutSeconds,
        int batchSize,
        long pollIntervalMs,
        boolean autoPublish) {

    public boolean available() {
        return enabled && deepseekApiKey != null && !deepseekApiKey.isBlank();
    }
}
