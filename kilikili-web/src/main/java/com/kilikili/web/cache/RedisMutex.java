package com.kilikili.web.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis SET NX 的轻量分布式互斥锁，用于缓存击穿时只放一个线程回源。
 * 解锁使用 Lua 校验 token，避免误删其他线程续期后的锁。
 */
@Component
public class RedisMutex {
    private static final Logger log = LoggerFactory.getLogger(RedisMutex.class);
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;

    public RedisMutex(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 抢锁成功返回 token，失败或 Redis 不可用返回 null（调用方走降级逻辑） */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (Exception exception) {
            log.warn("缓存互斥锁获取失败，降级为直接回源: key={}, error={}", key, exception.getMessage());
            return null;
        }
    }

    public void unlock(String key, String token) {
        if (token == null) return;
        try {
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    List.of(key), token);
        } catch (Exception exception) {
            log.warn("缓存互斥锁释放失败，等待 TTL 自动过期: key={}, error={}", key, exception.getMessage());
        }
    }
}
