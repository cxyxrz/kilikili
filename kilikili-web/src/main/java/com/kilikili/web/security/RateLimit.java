package com.kilikili.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流：基于 Redis 令牌桶，按用户（未登录时按 IP）维度控制访问频率。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 限流桶名称，默认取「类名#方法名」；同名接口共享配额 */
    String name() default "";

    /** 桶容量（允许的瞬时突发次数） */
    int capacity() default 20;

    /** 每秒补充的令牌数，即长期平均 QPS */
    double refillPerSecond() default 5;

    /** 是否按登录用户限流；false 时按 IP */
    boolean perUser() default true;
}
