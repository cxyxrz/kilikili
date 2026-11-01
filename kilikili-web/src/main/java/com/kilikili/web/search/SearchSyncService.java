package com.kilikili.web.search;

import com.kilikili.web.entity.SearchSyncTask;
import com.kilikili.web.mapper.SearchSyncMapper;
import com.kilikili.web.mapper.VideoMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DB → ES 双写补偿与对账。
 * <li>写路径在数据库事务内落一条 outbox 任务，即使 ES 暂时不可用也不丢更新；</li>
 * <li>后台每 30 秒扫一次待处理任务，失败按指数退避重试，超过次数标记 FAILED；</li>
 * <li>每 10 分钟对账一次：对比 MySQL 已发布 id 与 ES 文档 id，缺失补写、多余删除。</li>
 */
@Service
public class SearchSyncService {
    private static final Logger log = LoggerFactory.getLogger(SearchSyncService.class);
    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";
    private static final int SYNC_LIMIT = 100_000;

    private final SearchSyncMapper syncMapper;
    private final VideoMapper videoMapper;
    private final VideoSearchIndexService indexService;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int maxAttempts;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public SearchSyncService(SearchSyncMapper syncMapper, VideoMapper videoMapper,
                             VideoSearchIndexService indexService, MeterRegistry meterRegistry,
                             @Value("${kilikili.search.outbox-batch-size:50}") int batchSize,
                             @Value("${kilikili.search.outbox-max-attempts:8}") int maxAttempts) {
        this.syncMapper = syncMapper;
        this.videoMapper = videoMapper;
        this.indexService = indexService;
        this.meterRegistry = meterRegistry;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** 与数据库变更同事务调用；operation 取 UPSERT 或 DELETE */
    public void enqueue(long videoId, String operation) {
        try {
            syncMapper.enqueue(videoId, operation);
        } catch (Exception exception) {
            log.warn("搜索同步任务入队失败: videoId={}, operation={}, {}", videoId, operation, exception.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${kilikili.search.outbox-initial-delay-ms:5000}",
            fixedDelayString = "${kilikili.search.outbox-interval-ms:30000}")
    public void processPending() {
        if (!indexService.enabled() || !processing.compareAndSet(false, true)) return;
        try {
            List<SearchSyncTask> tasks = syncMapper.pending(batchSize);
            for (SearchSyncTask task : tasks) apply(task);
        } catch (Exception exception) {
            log.warn("搜索同步补偿任务扫描失败: {}", exception.getMessage());
        } finally {
            processing.set(false);
        }
    }

    private void apply(SearchSyncTask task) {
        try {
            if (UPSERT.equals(task.operation())) {
                indexService.indexVideoStrict(task.videoId());
            } else {
                indexService.deleteVideoStrict(task.videoId());
            }
            syncMapper.markDone(task.id());
            counter("success");
            log.info("搜索同步补偿成功: videoId={}, operation={}, attempts={}", task.videoId(), task.operation(), task.attempts());
        } catch (Exception exception) {
            String error = truncate(exception.getMessage() == null ? exception.toString() : exception.getMessage(), 480);
            int attempts = task.attempts() + 1;
            if (attempts >= maxAttempts) {
                syncMapper.markFailed(task.id(), error);
                counter("failed");
                log.error("搜索同步补偿超过最大重试次数: videoId={}, operation={}, attempts={}, error={}",
                        task.videoId(), task.operation(), attempts, error);
            } else {
                // 指数退避：2s、4s、8s... 上限 300s
                int delay = (int) Math.min(300, Math.pow(2, Math.min(attempts, 8)));
                syncMapper.markRetry(task.id(), delay, error);
                counter("retry");
                log.warn("搜索同步补偿失败，{}s 后重试: videoId={}, operation={}, attempts={}, error={}",
                        delay, task.videoId(), task.operation(), attempts, error);
            }
        }
    }

    /** 定时对账：以 MySQL 为准，修复 ES 中缺失/多余的文档 */
    @Scheduled(initialDelayString = "${kilikili.search.reconcile-initial-delay-ms:60000}",
            fixedDelayString = "${kilikili.search.reconcile-interval-ms:600000}")
    public void scheduledReconcile() {
        if (!indexService.enabled()) return;
        try {
            Map<String, Object> summary = reconcile();
            if (Boolean.TRUE.equals(summary.get("skipped"))) return;
            log.info("搜索索引对账完成: db={}, es={}, 补写={}, 删除={}, 失败={}",
                    summary.get("dbCount"), summary.get("esCount"), summary.get("upserted"),
                    summary.get("deleted"), summary.get("failed"));
        } catch (Exception exception) {
            log.warn("搜索索引对账失败: {}", exception.getMessage());
        }
    }

    /** 手动对账入口，返回本次比对与修复结果 */
    public Map<String, Object> reconcile() throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!indexService.enabled()) {
            summary.put("skipped", true);
            summary.put("reason", "search disabled");
            return summary;
        }
        if (!indexService.available()) {
            summary.put("skipped", true);
            summary.put("reason", "elasticsearch unavailable");
            return summary;
        }
        Set<String> dbIds = new HashSet<>();
        for (Long id : videoMapper.listPublishedIds(SYNC_LIMIT)) dbIds.add(String.valueOf(id));
        Set<String> esIds = indexService.listIndexedIds();

        int upserted = 0;
        int deleted = 0;
        int failed = 0;
        for (String id : dbIds) {
            if (esIds.contains(id)) continue;
            try {
                indexService.indexVideoStrict(Long.parseLong(id));
                upserted++;
            } catch (Exception exception) {
                failed++;
                enqueue(Long.parseLong(id), UPSERT);
                log.warn("对账补写失败，已转入补偿任务: videoId={}, {}", id, exception.getMessage());
            }
        }
        List<String> extras = new ArrayList<>();
        for (String id : esIds) if (!dbIds.contains(id)) extras.add(id);
        for (String id : extras) {
            try {
                indexService.deleteVideoStrict(Long.parseLong(id));
                deleted++;
            } catch (Exception exception) {
                failed++;
                enqueue(Long.parseLong(id), DELETE);
                log.warn("对账删除失败，已转入补偿任务: videoId={}, {}", id, exception.getMessage());
            }
        }
        if (upserted + deleted > 0) counter("repaired", upserted + deleted);
        summary.put("dbCount", dbIds.size());
        summary.put("esCount", esIds.size());
        summary.put("upserted", upserted);
        summary.put("deleted", deleted);
        summary.put("failed", failed);
        return summary;
    }

    /** 运维状态：别名、物理索引、文档数、任务积压情况 */
    public Map<String, Object> status() throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", indexService.enabled());
        status.put("available", indexService.available());
        status.put("aliasExists", indexService.aliasExists());
        status.put("indices", indexService.physicalIndices());
        status.put("docCount", indexService.documentCount());
        status.put("pending", syncMapper.countByStatus("PENDING"));
        status.put("failed", syncMapper.countByStatus("FAILED"));
        return status;
    }

    public int purgeDone() {
        return syncMapper.purgeDone();
    }

    private void counter(String result) {
        meterRegistry.counter("kilikili.search.outbox", "result", result).increment();
    }

    private void counter(String result, int value) {
        meterRegistry.counter("kilikili.search.reconcile", "result", result).increment(value);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
