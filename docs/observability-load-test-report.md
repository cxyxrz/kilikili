# KiliKili 可观测性与压测报告

> 覆盖内容：Prometheus 指标、全链路 traceId、慢 SQL 记录、wrk/JMeter 压测结果。
> 记录时间：2026-10-31

## 一、可观测性能力

### 1. Prometheus 指标

后端通过 Spring Boot Actuator + Micrometer 暴露指标端点：

```bash
curl http://localhost:7071/actuator/prometheus
```

已开放端点：`health,info,prometheus,metrics`（`kilikili-web`、`kilikili-admin` 均配置）。

> admin 侧已在重新打包的 jar 上验证：临时端口 7072 访问 `/actuator/prometheus` 返回 200（含 `http_server_requests_*`），`X-Trace-Id` 回填正常。注意：若 admin 仍由 IDE 旧进程运行，需要重启以加载新增的 prometheus registry 依赖。

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `http_server_requests_seconds_*` | Histogram | 所有 HTTP 请求耗时，含 P50/P90/P95/P99 |
| `jvm_memory_used_bytes` / `jvm_gc_*` | Gauge/Counter | JVM 内存与 GC |
| `kilikili_sql_duration_seconds_*` | Timer | 按 Mapper 方法统计 SQL 耗时 |
| `kilikili_sql_slow_total{statement}` | Counter | 慢 SQL 次数 |
| `kilikili_rate_limited_total{rule}` | Counter | 限流拦截次数（按规则） |
| `kilikili_idempotent_rejected_total{state}` | Counter | 幂等拒绝次数（PROCESSING/DONE） |

公共标签：`application=kilikili-web`（`management.metrics.tags.application`）。
HTTP 百分位直方图已开启，可直接计算 P50/P90/P95/P99。

核心实现：
- [SlowSqlInterceptor.java](file:///Users/hrs/IdeaProjects/kilikili/kilikili-common/src/main/java/com/kilikili/common/observability/SlowSqlInterceptor.java)
- [BusinessMetrics.java](file:///Users/hrs/IdeaProjects/kilikili/kilikili-web/src/main/java/com/kilikili/web/observability/BusinessMetrics.java)

### 2. 全链路 traceId

- HTTP 入口由 [TraceIdFilter.java](file:///Users/hrs/IdeaProjects/kilikili/kilikili-common/src/main/java/com/kilikili/common/observability/TraceIdFilter.java) 读取请求头 `X-Trace-Id`，缺失则生成 16 位 ID；写入 MDC 并回填响应头 `X-Trace-Id`。
- 日志格式统一为 `... [trace=%X{traceId:-}] ...`，业务日志与 SQL 日志都带同一 traceId。
- RocketMQ 消息通过 user property `traceId` 透传，消费端重新写入 MDC，Producer → Consumer 日志可串联。
- 跨域已暴露 `X-Trace-Id`（`CorsConfig#setExposedHeaders`）。

验证示例：

```bash
curl -H "X-Trace-Id: trace-log-demo-01" http://localhost:7071/api/videos/1/play
# 响应头 X-Trace-Id: trace-log-demo-01
# 日志: [trace=trace-log-demo-01] 视频开始播放
```

### 3. 慢 SQL 记录

- 阈值：`kilikili.observability.slow-sql-threshold-ms`，默认 200ms，可用环境变量 `SLOW_SQL_THRESHOLD_MS` 覆盖。
- 拦截范围：MyBatis `Executor.query`（4/6 参数重载）与 `update`。
- 每次查询上报 `kilikili.sql.duration`；超阈值额外 WARN + `kilikili.sql.slow` 计数。
- 独立文件：`logs/slow-sql.log`（10MB 滚动、保留 30 天、最多 200MB），主日志仍保留。

日志样例（阈值临时调为 50ms 压测）：

```
2026-10-31 14:41:46.541 [http-nio-7071-exec-62] [trace=e96e80f3327c4ef7] WARN c.k.c.observability.SlowSqlInterceptor
- [slow-sql] cost=55ms threshold=50ms mapper=VideoMapper.listPublished sql=SELECT ... FROM video v JOIN account a ON a.id=v.author_id WHERE v.status='PUBLISHED' ORDER BY v.created_at DESC LIMIT ?
```

## 二、压测环境与工具

| 项 | 值 |
| --- | --- |
| 机器 | macOS（本地开发机） |
| 后端 | Spring Boot 3.4，JDK 21，端口 7071 |
| 依赖 | MySQL 8 / Redis / MinIO / Elasticsearch / SRS / RocketMQ |
| 数据 | `video` 表已发布视频若干，推荐结果命中 Redis 缓存 |
| 压测工具 | JMeter 5.6.3（非 GUI 模式）、ApacheBench 2.3 |
| 慢 SQL 阈值 | 压测期间临时设为 50ms，便于观察 |

> wrk 说明：`brew install wrk` 依赖 Homebrew 自动更新从 `ghcr.io` 拉取 portable-ruby，网络不可达导致安装失败；Docker 方式亦因 `registry-1.docker.io` 超时失败。因此改用 JMeter 作为主压测工具，ApacheBench 作为补充。

JMeter 运行方式：

```bash
jmeter -n -t read-plan.jmx -l results-videos.jtl \
  -Jpath=/api/videos -Jthreads=100 -Jduration=30
```

计划使用 HTTP Keep-Alive，结果统计用 Python 从 JTL 中解析 `elapsed` 字段计算百分位。

## 三、压测结果

### 场景一：`GET /api/videos`（列表，走 MySQL）

| 指标 | 值 |
| --- | --- |
| 并发线程 | 100 |
| 持续时长 | 30s |
| 总样本 | 391,453 |
| 成功率 | 100%（全部 200） |
| 吞吐 | **13,034 req/s** |
| 平均耗时 | 7.02 ms |
| P50 / P90 / P95 / P99 | 7 / 12 / 14 / 21 ms |
| Max | 68 ms |
| 慢 SQL 次数 | 36（> 50ms） |

### 场景二：`GET /api/recommendations`（缓存路径，走 Redis）

| 指标 | 值 |
| --- | --- |
| 并发线程 | 100 |
| 持续时长 | 30s |
| 总样本 | 2,051,483 |
| 成功率 | 100%（全部 200） |
| 吞吐 | **68,319 req/s** |
| 平均耗时 | 1.32 ms |
| P50 / P95 / P99 | 1 / 2 / 3 ms |
| Max | 82 ms |

缓存路径吞吐约为数据库路径的 5 倍，耗时降低约 5 倍，说明推荐缓存命中率良好。

### 场景三：`POST /api/auth/login`（限流验证）

| 指标 | 值 |
| --- | --- |
| 并发线程 | 20 |
| 持续时长 | 15s |
| 总样本 | 409,225 |
| 状态分布 | 200 × 17，429 × 409,208 |
| P50 / P95 / P99 | 1 / 1 / 2 ms |
| Max | 157 ms |

限流生效：`kilikili_rate_limited_total{rule="auth:login"} = 409208`，与 429 数量一致；仅放行额度内的 17 次请求。

### 补充：ApacheBench

| 接口 | 参数 | 吞吐 | P95 |
| --- | --- | --- | --- |
| `/api/videos` | `-c 50 -n 3000` | 2,832 req/s | 53 ms |
| `/api/recommendations` | `-c 50 -n 2000` | 10,422 req/s | 5 ms |

ab 结果与 JMeter 趋势一致（ab 存在少量 Length 差异，非业务失败）。

## 四、指标快照（压测结束时）

```
kilikili_sql_duration_seconds_count{statement="VideoMapper.listPublished",...} 大量样本
kilikili_sql_slow_total{statement="VideoMapper.listPublished"} 36
kilikili_rate_limited_total{rule="auth:login"} 409208
kilikili_idempotent_rejected_total{state="DONE"} > 0
http_server_requests_seconds_count{uri="/api/videos",status="200"} 约 391k
```

`slow-sql.log` 在 50ms 阈值下共 141 行，最慢 `VideoMapper.listPublished` 约 111ms，每条均带 traceId。

## 五、瓶颈分析与优化建议

1. **列表接口是主要瓶颈**：`/api/videos` 吞吐约为缓存接口的 1/5，耗时高 5 倍，且产生慢 SQL。建议：
   - 为 `video(status, created_at)` 建联合索引，避免全表过滤 + 排序。
   - 列表首屏缓存到 Redis（短 TTL + 发布时失效），与推荐缓存策略一致。
   - 分页改为游标分页（`created_at < ?`），避免深分页排序。
2. **慢 SQL 集中在 `VideoMapper.listPublished`**：JOIN `account` 取作者信息，可考虑冗余 `author_name/author_avatar` 到 `video` 或单独缓存，减少 JOIN。
3. **连接池**：100 并发下 P99 21ms 尚可，继续加压可关注 HikariCP 等待时间（`hikaricp_connections_pending`）。
4. **限流可用性**：登录快速失败（1ms P50）符合预期，但需关注 429 比例监控，防止误伤正常用户；可结合 `kilikili_rate_limited_total` 做告警。
5. **可观测性闭环**：建议接入 Prometheus + Grafana，配置：
   - HTTP P99 > 100ms 告警
   - `kilikili_sql_slow_total` 增长率告警
   - 慢 SQL 日志按 traceId 关联业务链路排查。

## 六、复现步骤

1. 启动依赖（MySQL/Redis/MinIO/ES/SRS/RocketMQ）与后端：
   ```bash
   cd kilikili-web && java -jar target/kilikili-web-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
   ```
2. 查看指标：`curl http://localhost:7071/actuator/prometheus`
3. 压测：`jmeter -n -t /tmp/jmeter/read-plan.jmx -l result.jtl -Jpath=/api/videos -Jthreads=100 -Jduration=30`
4. 慢 SQL 观察：`tail -f kilikili-web/logs/slow-sql.log`
5. 恢复默认阈值（200ms）后重启，避免线上误报。
