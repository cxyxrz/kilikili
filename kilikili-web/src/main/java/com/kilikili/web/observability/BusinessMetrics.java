package com.kilikili.web.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 业务指标埋点：把限流、幂等拦截等关键风控事件上报 Prometheus，
 * 便于配置告警（如某接口限流次数突增说明被刷或流量异常）。
 */
@Component
public class BusinessMetrics {
    private final MeterRegistry meterRegistry;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 限流拦截次数，按限流规则区分 */
    public void rateLimited(String rule) {
        meterRegistry.counter("kilikili.rate.limited", "rule", rule).increment();
    }

    /** 幂等拦截次数，按处理状态区分（PROCESSING/DONE） */
    public void idempotentRejected(String state) {
        meterRegistry.counter("kilikili.idempotent.rejected", "state", state).increment();
    }
}
