package com.kilikili.web.config;

import com.kilikili.web.security.AuthTokenInterceptor;
import com.kilikili.web.security.IdempotencyInterceptor;
import com.kilikili.web.security.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全拦截链：先做 JWT 续期判断，再限流，最后幂等校验。
 */
@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {
    private final AuthTokenInterceptor authTokenInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final IdempotencyInterceptor idempotencyInterceptor;

    public SecurityWebConfig(AuthTokenInterceptor authTokenInterceptor,
                             RateLimitInterceptor rateLimitInterceptor,
                             IdempotencyInterceptor idempotencyInterceptor) {
        this.authTokenInterceptor = authTokenInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authTokenInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**").order(1);
        registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/**").order(2);
    }
}
