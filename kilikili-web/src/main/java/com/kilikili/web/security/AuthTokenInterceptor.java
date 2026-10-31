package com.kilikili.web.security;

import com.kilikili.common.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 访问令牌自动续期：请求携带的 JWT 临近过期时签发新令牌，
 * 通过响应头 X-Auth-Token 下发，前端拦截后替换本地令牌，实现无感续期。
 * 注意：@ResponseBody 接口在 postHandle 之前就已写出响应体，此时响应已提交、
 * 无法再追加头，因此续期头必须在 preHandle 阶段写入。
 */
@Component
public class AuthTokenInterceptor implements HandlerInterceptor {
    public static final String NEW_TOKEN_HEADER = "X-Auth-Token";

    private final AuthService authService;

    public AuthTokenInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return true;
        Optional<String> renewed = authService.renewIfNeeded(authorization);
        renewed.ifPresent(token -> response.setHeader(NEW_TOKEN_HEADER, token));
        return true;
    }
}
