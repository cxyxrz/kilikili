package com.kilikili.web.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 推荐页缓存治理配置。
 * TTL 抖动与本地 L1 用于防雪崩，空值 TTL 用于防穿透，
 * 锁超时/等待用于防击穿，延迟毫秒用于延迟双删，热点阈值用于热点 key 探测。
 */
@ConfigurationProperties(prefix = "kilikili.cache")
public record CacheGovernanceProperties(
        int recommendTtlSeconds,
        int ttlJitterSeconds,
        int nullTtlSeconds,
        int localTtlMs,
        int lockTtlMs,
        int lockWaitMs,
        int doubleDeleteDelayMs,
        int categoryCacheSeconds,
        int hotKeyWindowMs,
        int hotKeyThreshold,
        int hotKeyExtraTtlSeconds,
        int hotKeyMaxSize,
        int hotKeyFlushIntervalMs) {

    public int recommendTtlSeconds() {
        return recommendTtlSeconds <= 0 ? 300 : recommendTtlSeconds;
    }

    public int ttlJitterSeconds() {
        return Math.max(0, ttlJitterSeconds);
    }

    public int nullTtlSeconds() {
        return nullTtlSeconds <= 0 ? 60 : nullTtlSeconds;
    }

    public int localTtlMs() {
        return localTtlMs <= 0 ? 30_000 : localTtlMs;
    }

    public int lockTtlMs() {
        return lockTtlMs <= 0 ? 3000 : lockTtlMs;
    }

    public int lockWaitMs() {
        return Math.max(0, lockWaitMs);
    }

    public int doubleDeleteDelayMs() {
        return doubleDeleteDelayMs <= 0 ? 500 : doubleDeleteDelayMs;
    }

    public int categoryCacheSeconds() {
        return categoryCacheSeconds <= 0 ? 300 : categoryCacheSeconds;
    }

    public int hotKeyWindowMs() {
        return hotKeyWindowMs <= 0 ? 10_000 : hotKeyWindowMs;
    }

    public int hotKeyThreshold() {
        return hotKeyThreshold <= 0 ? 50 : hotKeyThreshold;
    }

    public int hotKeyExtraTtlSeconds() {
        return Math.max(0, hotKeyExtraTtlSeconds);
    }

    public int hotKeyMaxSize() {
        return hotKeyMaxSize <= 0 ? 200 : hotKeyMaxSize;
    }
}
