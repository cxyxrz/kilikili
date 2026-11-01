package com.kilikili.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 慢 SQL 记录：拦截 MyBatis Executor 的 query/update，统计执行耗时。
 * 超过阈值写入 WARN 日志（logback 中单独输出到 slow-sql.log，且带 traceId），
 * 同时上报 Micrometer 指标，便于在 Prometheus/Grafana 中观察 SQL 耗时分布。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        org.apache.ibatis.cache.CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(SlowSqlInterceptor.class);

    private final MeterRegistry meterRegistry;
    private final long slowThresholdMs;

    public SlowSqlInterceptor(MeterRegistry meterRegistry,
                              @Value("${kilikili.observability.slow-sql-threshold-ms:200}") long slowThresholdMs) {
        this.meterRegistry = meterRegistry;
        this.slowThresholdMs = slowThresholdMs <= 0 ? 200 : slowThresholdMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        String statementId = shortName(statement.getId());
        long startedAt = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            meterRegistry.timer("kilikili.sql.duration", "statement", statementId)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
            if (elapsedMs >= slowThresholdMs) {
                log.warn("[slow-sql] cost={}ms threshold={}ms mapper={} sql={}",
                        elapsedMs, slowThresholdMs, statementId, boundSql(invocation, statement));
                meterRegistry.counter("kilikili.sql.slow", "statement", statementId).increment();
            }
        }
    }

    /** 取 MyBatis 已解析的 BoundSql，避免重复构建动态 SQL */
    private String boundSql(Invocation invocation, MappedStatement statement) {
        try {
            Object parameter = invocation.getArgs()[1];
            BoundSql boundSql = invocation.getArgs().length >= 6
                    ? (BoundSql) invocation.getArgs()[5]
                    : statement.getBoundSql(parameter);
            String sql = boundSql.getSql();
            return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        } catch (Exception exception) {
            return "<unavailable>";
        }
    }

    /** com.kilikili.web.mapper.VideoMapper.listPublished -> VideoMapper.listPublished，控制指标标签基数 */
    private String shortName(String id) {
        int lastDot = id.lastIndexOf('.');
        int previousDot = id.lastIndexOf('.', lastDot - 1);
        return previousDot < 0 ? id : id.substring(previousDot + 1);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 阈值统一由配置中心管理，无需 XML 参数
    }
}
