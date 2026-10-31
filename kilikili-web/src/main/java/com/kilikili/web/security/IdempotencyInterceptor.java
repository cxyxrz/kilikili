package com.kilikili.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

/**
 * 幂等提交拦截：客户端为写请求带上 X-Idempotency-Key，服务端用 Redis SETNX 抢占处理权。
 * 处理中/已处理的相同键直接拒绝；处理失败释放键，允许用户重试。
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String KEY_PREFIX = "kilikili:idem:";
    private static final String PROCESSING = "PROCESSING";
    private static final String DONE = "DONE";
    private static final String REQUEST_KEY_ATTR = IdempotencyInterceptor.class.getName() + ".key";

    private final StringRedisTemplate redisTemplate;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(StringRedisTemplate redisTemplate, AuthService authService, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        if (HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.HEAD.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (idempotent == null) idempotent = handlerMethod.getBeanType().getAnnotation(Idempotent.class);
        if (idempotent == null) return true;

        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            writeJson(response, 400, "缺少幂等键 X-Idempotency-Key，请刷新页面后重试");
            return false;
        }

        String redisKey = KEY_PREFIX + subject(request) + ":" + request.getMethod() + ":"
                + request.getRequestURI() + ":" + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING, Duration.ofSeconds(idempotent.ttlSeconds()));
        if (Boolean.TRUE.equals(acquired)) {
            request.setAttribute(REQUEST_KEY_ATTR, redisKey);
            return true;
        }

        String state = redisTemplate.opsForValue().get(redisKey);
        log.warn("重复提交被拦截: key={}, state={}", redisKey, state);
        writeJson(response, 409, DONE.equals(state)
                ? "该操作已完成，请勿重复提交"
                : "请求正在处理中，请勿重复提交");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object redisKey = request.getAttribute(REQUEST_KEY_ATTR);
        if (redisKey == null) return;
        if (exception != null || response.getStatus() >= 400) {
            // 处理失败时释放键，用户可以带着同一键重试
            redisTemplate.delete((String) redisKey);
            return;
        }
        redisTemplate.opsForValue().set((String) redisKey, DONE, Duration.ofSeconds(600));
    }

    private String subject(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                return "u:" + authService.requireUserId(authorization);
            } catch (Exception ignored) {
            }
        }
        return "ip:" + RateLimitInterceptor.clientIp(request);
    }

    private void writeJson(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
