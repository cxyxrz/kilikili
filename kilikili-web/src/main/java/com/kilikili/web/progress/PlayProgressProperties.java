package com.kilikili.web.progress;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 播放进度合并写配置：客户端每 15 秒上报一次，Redis 缓存最新进度，
 * 延迟任务到期后与上次落库值比对，只有进度发生变化才写 MySQL。
 */
@ConfigurationProperties(prefix = "kilikili.progress")
public record PlayProgressProperties(
        boolean enabled,
        long delayMs,
        long flushIntervalMs,
        long cacheTtlSeconds,
        int batchSize) {
}
