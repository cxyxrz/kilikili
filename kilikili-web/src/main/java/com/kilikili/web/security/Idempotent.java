package com.kilikili.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等提交：要求请求携带 X-Idempotency-Key，Redis 以 SETNX 记录处理状态，
 * 同一用户对同一接口重复提交相同 key 时直接拒绝，避免重复投稿、重复点赞等。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /** 幂等键有效期（秒），在此窗口内重复提交都会被拦截 */
    int ttlSeconds() default 600;
}
