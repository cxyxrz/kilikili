package com.kilikili.common.auth;

import com.kilikili.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String PREFIX = "kilikili:session:";
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public AuthService(StringRedisTemplate redisTemplate, @Value("${kilikili.auth.session-ttl-days:7}") long ttlDays) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public String createSession(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PREFIX + token, String.valueOf(userId), ttl);
        log.info("已创建用户会话: userId={}, ttlDays={}", userId, ttl.toDays());
        return token;
    }

    public long requireUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("拒绝未携带 Bearer Token 的请求");
            throw new BusinessException("请先登录");
        }
        String userId = redisTemplate.opsForValue().get(PREFIX + authorization.substring(7));
        if (userId == null) {
            log.warn("拒绝已失效的会话请求");
            throw new BusinessException("登录已过期，请重新登录");
        }
        return Long.parseLong(userId);
    }
}
