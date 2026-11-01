package com.kilikili.common.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 全链路追踪上下文：traceId 存放在 MDC 中，日志通过 %X{traceId} 输出。
 * HTTP 入口由 {@link TraceIdFilter} 写入，RocketMQ 消费端从消息属性恢复，
 * 异步线程可通过 {@link #withTraceId(String, Runnable)} 手动传递。
 */
public final class TraceContext {
    /** 与 logback-spring.xml 中的 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";
    /** 上下游统一透传的请求/响应头 */
    public static final String HEADER = "X-Trace-Id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_.-]{8,64}");

    private TraceContext() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 上游传入合法 traceId 则沿用，否则生成新的，保证链路不被污染 */
    public static String normalize(String traceId) {
        return traceId != null && VALID.matcher(traceId).matches() ? traceId : generate();
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** 在指定 traceId 下执行任务，结束后恢复原上下文 */
    public static void withTraceId(String traceId, Runnable action) {
        String previous = MDC.get(MDC_KEY);
        try {
            MDC.put(MDC_KEY, normalize(traceId));
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
