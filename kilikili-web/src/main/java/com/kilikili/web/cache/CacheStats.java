package com.kilikili.web.cache;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存访问统计：用于观测命中率、回源次数、锁竞争与 Redis 故障降级情况。
 */
@Component
public class CacheStats {
    private final LongAdder localHit = new LongAdder();
    private final LongAdder redisHit = new LongAdder();
    private final LongAdder miss = new LongAdder();
    private final LongAdder nullHit = new LongAdder();
    private final LongAdder dbLoad = new LongAdder();
    private final LongAdder lockContended = new LongAdder();
    private final LongAdder dbFallback = new LongAdder();
    private final LongAdder redisFailure = new LongAdder();

    public void localHit() {
        localHit.increment();
    }

    public void redisHit() {
        redisHit.increment();
    }

    public void miss() {
        miss.increment();
    }

    public void nullHit() {
        nullHit.increment();
    }

    public void dbLoad() {
        dbLoad.increment();
    }

    public void lockContended() {
        lockContended.increment();
    }

    public void dbFallback() {
        dbFallback.increment();
    }

    public void redisFailure() {
        redisFailure.increment();
    }

    public Map<String, Object> snapshot() {
        long hits = localHit.sum() + redisHit.sum();
        long total = hits + miss.sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("localHit", localHit.sum());
        result.put("redisHit", redisHit.sum());
        result.put("miss", miss.sum());
        result.put("hitRate", total == 0 ? 0d : Math.round((double) hits / total * 10000d) / 10000d);
        result.put("nullHit", nullHit.sum());
        result.put("dbLoad", dbLoad.sum());
        result.put("lockContended", lockContended.sum());
        result.put("dbFallback", dbFallback.sum());
        result.put("redisFailure", redisFailure.sum());
        return result;
    }
}
