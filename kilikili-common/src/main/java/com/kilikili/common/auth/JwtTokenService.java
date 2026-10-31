package com.kilikili.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 访问令牌：subject 存用户 ID，jti 作为会话标识写入 Redis，
 * 这样既保留 JWT 的无状态校验（签名/过期），又支持服务端撤销与续期。
 */
@Service
public class JwtTokenService {
    private final SecretKey key;
    private final Duration accessTtl;

    public JwtTokenService(@Value("${kilikili.auth.jwt-secret:kilikili-dev-jwt-secret-please-change-0123456789}") String secret,
                           @Value("${kilikili.auth.access-ttl-minutes:120}") long accessTtlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥至少 256 位，开发环境默认值不足时补齐，避免启动失败
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int index = bytes.length; index < padded.length; index++) padded[index] = (byte) '0';
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes <= 0 ? 120 : accessTtlMinutes);
    }

    public IssuedToken issue(long userId) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(accessTtl);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    /** 验签并解析；签名错误、过期或格式非法都返回空 */
    public Optional<TokenPayload> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenPayload(
                    Long.parseLong(claims.getSubject()),
                    claims.getId(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean looksLikeJwt(String token) {
        return token != null && token.chars().filter(character -> character == '.').count() == 2;
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    public record TokenPayload(long userId, String jti, Instant expiresAt) {
    }
}
