package com.kilikili.web.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 应用级热点 key 探测：按滑动窗口统计推荐缓存 key 的访问次数，
 * 超过阈值的 key 视为热点并写入 Redis ZSET，供缓存延长 TTL 与运维观测。
 * 之所以不用 Redis OBJECT FREQ，是因为需要在不开启 LFU 淘汰策略时也能识别热点。
 */
@Component
public class HotKeyDetector {
    private static final Logger log = LoggerFactory.getLogger(HotKeyDetector.class);
    private static final String ZSET_KEY = "kilikili:hotkey:recommend";
    private static final Duration ZSET_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final CacheGovernanceProperties properties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> extendedAt = new ConcurrentHashMap<>();
    /** 热点状态保持 3 个窗口，避免刚汇总完就丢失热点标记导致 TTL 不再续期 */
    private final Map<String, Long> hotUntil = new ConcurrentHashMap<>();

    public HotKeyDetector(StringRedisTemplate redisTemplate, CacheGovernanceProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /** 每次缓存访问都记一次窗口计数 */
    public void record(String key) {
        long now = System.currentTimeMillis();
        counters.computeIfAbsent(key, ignored -> new WindowCounter(now)).hit(now, properties.hotKeyWindowMs());
    }

    public boolean isHot(String key) {
        long now = now();
        Long until = hotUntil.get(key);
        if (until != null && now < until) return true;
        WindowCounter counter = counters.get(key);
        return counter != null
                && counter.count(now, properties.hotKeyWindowMs()) >= properties.hotKeyThreshold();
    }

    /** 热点 key 的续期做了节流：同窗口内只延长一次，避免每次读都写 Redis */
    public boolean markExtended(String key) {
        long now = now();
        Long last = extendedAt.get(key);
        if (last != null && now - last < properties.hotKeyWindowMs()) return false;
        extendedAt.put(key, now);
        return true;
    }

    /** 周期性把窗口内的热点 key 汇总到 Redis ZSET，同时清理过期计数 */
    @Scheduled(fixedDelayString = "${kilikili.cache.hot-key-flush-interval-ms:10000}")
    public void flush() {
        long now = now();
        int threshold = properties.hotKeyThreshold();
        List<String> hotInWindow = new ArrayList<>();
        counters.forEach((key, counter) -> {
            long count = counter.count(now, properties.hotKeyWindowMs());
            if (count >= threshold) {
                hotInWindow.add(key + "(" + count + ")");
                hotUntil.put(key, now + properties.hotKeyWindowMs() * 3L);
                try {
                    redisTemplate.opsForZSet().incrementScore(ZSET_KEY, key, count);
                } catch (Exception exception) {
                    log.warn("热点 key 写入 Redis 失败: key={}, error={}", key, exception.getMessage());
                }
                counters.remove(key);
            } else if (count == 0) {
                counters.remove(key, counter);
            }
        });
        extendedAt.keySet().removeIf(key -> now - extendedAt.get(key) > properties.hotKeyWindowMs() * 6L);
        hotUntil.entrySet().removeIf(entry -> now > entry.getValue());
        if (!hotInWindow.isEmpty()) {
            log.info("热点 key 探测到 {} 个热点: {}", hotInWindow.size(), hotInWindow);
            trimZset();
        }
    }

    private void trimZset() {
        try {
            Long size = redisTemplate.opsForZSet().zCard(ZSET_KEY);
            if (size != null && size > properties.hotKeyMaxSize()) {
                redisTemplate.opsForZSet().removeRange(ZSET_KEY, 0L,
                        (long) size - properties.hotKeyMaxSize() - 1L);
            }
            redisTemplate.expire(ZSET_KEY, ZSET_TTL);
        } catch (Exception exception) {
            log.warn("热点 key 榜单裁剪失败: {}", exception.getMessage());
        }
    }

    public List<Map<String, Object>> topHotKeys(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> result = new ArrayList<>();
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = redisTemplate.opsForZSet().reverseRangeWithScores(ZSET_KEY, 0, size - 1L);
        } catch (Exception exception) {
            log.warn("读取热点 key 榜单失败: {}", exception.getMessage());
            tuples = null;
        }
        if (tuples != null) {
            long now = now();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", tuple.getValue());
                item.put("totalCount", tuple.getScore() == null ? 0L : tuple.getScore().longValue());
                WindowCounter counter = counters.get(tuple.getValue());
                item.put("windowCount", counter == null ? 0L : counter.count(now, properties.hotKeyWindowMs()));
                item.put("hot", isHot(tuple.getValue()));
                result.add(item);
            }
        }
        return result;
    }

    public Map<String, Object> localWindowSnapshot() {
        long now = now();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("windowSeconds", properties.hotKeyWindowMs() / 1000);
        snapshot.put("threshold", properties.hotKeyThreshold());
        snapshot.put("trackedKeys", counters.size());
        List<String> keys = counters.entrySet().stream()
                .filter(entry -> entry.getValue().count(now, properties.hotKeyWindowMs()) > 0)
                .sorted((left, right) -> Long.compare(
                        right.getValue().count(now, properties.hotKeyWindowMs()),
                        left.getValue().count(now, properties.hotKeyWindowMs())))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();
        snapshot.put("currentWindowKeys", keys);
        return snapshot;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static final class WindowCounter {
        private volatile long windowStart;
        private final AtomicLong count = new AtomicLong();

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }

        private void hit(long now, int windowMs) {
            if (now - windowStart > windowMs) {
                synchronized (this) {
                    if (now - windowStart > windowMs) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            count.incrementAndGet();
        }

        private long count(long now, int windowMs) {
            return now - windowStart > windowMs ? 0 : count.get();
        }
    }
}
