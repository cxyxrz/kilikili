package com.kilikili.common.auth;

import com.kilikili.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 登录会话：对外签发 JWT，Redis 以 jti 为键保存会话（可撤销、可续期），
 * 同时兼容此前签发的随机 opaque token，避免升级后旧会话立即失效。
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String PREFIX = "kilikili:session:";
    private final StringRedisTemplate redisTemplate;
    private final JwtTokenService jwtTokenService;
    private final Duration ttl;
    private final Duration renewThreshold;

    public AuthService(StringRedisTemplate redisTemplate, JwtTokenService jwtTokenService,
                       @Value("${kilikili.auth.session-ttl-days:7}") long ttlDays,
                       @Value("${kilikili.auth.renew-threshold-minutes:30}") long renewThresholdMinutes) {
        this.redisTemplate = redisTemplate;
        this.jwtTokenService = jwtTokenService;
        this.ttl = Duration.ofDays(ttlDays);
        this.renewThreshold = Duration.ofMinutes(renewThresholdMinutes <= 0 ? 30 : renewThresholdMinutes);
    }

    /** 登录/注册成功后签发 JWT，并在 Redis 记录会话，用于撤销与续期 */
    public String createSession(long userId) {
        JwtTokenService.IssuedToken issued = jwtTokenService.issue(userId);
        redisTemplate.opsForValue().set(PREFIX + issued.jti(), String.valueOf(userId), ttl);
        log.info("已创建用户会话: userId={}, sessionTtlDays={}", userId, ttl.toDays());
        return issued.token();
    }

    public long requireUserId(String authorization) {
        String token = extractToken(authorization);
        long userId = resolveUserId(token)
                .orElseThrow(() -> {
                    log.warn("拒绝已失效的会话请求");
                    return new BusinessException("登录已过期，请重新登录");
                });
        return userId;
    }

    /**
     * 访问令牌临近过期时自动续期：签发新 JWT 并登记新会话，旧令牌保留到自身过期为止，
     * 避免并发请求携带旧令牌时被误判失效。
     *
     * @return 新令牌；无需续期或令牌无效时为空
     */
    public Optional<String> renewIfNeeded(String authorization) {
        String token;
        try {
            token = extractToken(authorization);
        } catch (BusinessException exception) {
            return Optional.empty();
        }
        if (!jwtTokenService.looksLikeJwt(token)) return Optional.empty();
        Optional<JwtTokenService.TokenPayload> payload = jwtTokenService.parse(token);
        if (payload.isEmpty()) return Optional.empty();
        JwtTokenService.TokenPayload current = payload.get();
        if (Instant.now().plus(renewThreshold).isBefore(current.expiresAt())) return Optional.empty();
        String userId = redisTemplate.opsForValue().get(PREFIX + current.jti());
        if (userId == null) return Optional.empty();

        JwtTokenService.IssuedToken issued = jwtTokenService.issue(current.userId());
        redisTemplate.opsForValue().set(PREFIX + issued.jti(), userId, ttl);
        log.info("JWT 续期完成: userId={}, oldJti={}, newJti={}", current.userId(), current.jti(), issued.jti());
        return Optional.of(issued.token());
    }

    /** 退出登录：删除当前会话，之后该 JWT 即使未过期也无法再访问接口 */
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return;
        String token = authorization.substring(7).trim();
        String jti = token;
        if (jwtTokenService.looksLikeJwt(token)) {
            jti = jwtTokenService.parse(token).map(JwtTokenService.TokenPayload::jti).orElse(null);
        }
        if (jti != null) redisTemplate.delete(PREFIX + jti);
        log.info("用户会话已注销: tokenPrefix={}", jti == null ? "unknown" : jti.substring(0, Math.min(8, jti.length())));
    }

    private Optional<Long> resolveUserId(String token) {
        if (jwtTokenService.looksLikeJwt(token)) {
            Optional<JwtTokenService.TokenPayload> payload = jwtTokenService.parse(token);
            if (payload.isEmpty()) return Optional.empty();
            String userId = redisTemplate.opsForValue().get(PREFIX + payload.get().jti());
            return userId == null ? Optional.empty() : Optional.of(Long.parseLong(userId));
        }
        // 兼容升级前的随机 token
        String userId = redisTemplate.opsForValue().get(PREFIX + token);
        return userId == null ? Optional.empty() : Optional.of(Long.parseLong(userId));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("拒绝未携带 Bearer Token 的请求");
            throw new BusinessException("请先登录");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) throw new BusinessException("请先登录");
        return token;
    }
}
