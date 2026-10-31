package com.kilikili.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * Redis 令牌桶限流：Lua 脚本保证「读令牌 - 计算补充 - 扣减」原子执行，
 * 限流维度为登录用户或客户端 IP，超限返回 429 与 Retry-After。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String KEY_PREFIX = "kilikili:rate:";
    private static final String BUCKET_TTL_SECONDS = "120";

    /** KEYS[1]=桶键；ARGV：容量、每秒补充、当前秒、过期秒 */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = capacity end
            if ts == nil then ts = now end
            local delta = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + delta * refill)
            if tokens < 1 then
              redis.call('HSET', key, 'tokens', tokens, 'ts', now)
              redis.call('EXPIRE', key, ttl)
              return 0
            end
            tokens = tokens - 1
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('EXPIRE', key, ttl)
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> script;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate, AuthService authService, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.script = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        RateLimit limit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (limit == null) limit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        if (limit == null) return true;

        String name = limit.name().isBlank()
                ? handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName()
                : limit.name();
        String key = KEY_PREFIX + name + ":" + subject(request, limit.perUser());
        long nowSeconds = System.currentTimeMillis() / 1000;
        Long allowed = redisTemplate.execute(script, List.of(key),
                String.valueOf(limit.capacity()),
                String.valueOf(limit.refillPerSecond()),
                String.valueOf(nowSeconds),
                BUCKET_TTL_SECONDS);
        if (allowed != null && allowed == 1L) return true;

        log.warn("触发限流: key={}, uri={}", key, request.getRequestURI());
        response.setHeader("Retry-After", String.valueOf(Math.max(1,
                (int) Math.ceil(1.0 / Math.max(0.1, limit.refillPerSecond())))));
        writeJson(response, 429, "操作过于频繁，请稍后再试");
        return false;
    }

    private String subject(HttpServletRequest request, boolean perUser) {
        if (perUser) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                try {
                    return "u:" + authService.requireUserId(authorization);
                } catch (Exception ignored) {
                    // 令牌无效时退回 IP 维度，避免限流成为鉴权短路
                }
            }
        }
        return "ip:" + clientIp(request);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    private void writeJson(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
