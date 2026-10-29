package com.kilikili.web.transcode;

import com.kilikili.web.mapper.TranscodeAttemptMapper;
import com.kilikili.web.mapper.TranscodeProgressMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 转码可观测性：成功率、平均/P95 耗时、重试率、队列积压。
 * 统计口径基于 transcode_task_attempt（每次尝试一行），
 * 成功率 = 成功尝试数 / 已结束尝试数，能反映自动重试后的最终成功情况。
 */
@Service
public class TranscodeMetricsService {
    private final TranscodeAttemptMapper attemptMapper;
    private final TranscodeProgressMapper progressMapper;
    private final TranscodeService transcodeService;

    public TranscodeMetricsService(TranscodeAttemptMapper attemptMapper, TranscodeProgressMapper progressMapper,
                                   TranscodeService transcodeService) {
        this.attemptMapper = attemptMapper;
        this.progressMapper = progressMapper;
        this.transcodeService = transcodeService;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> summary = attemptMapper.summary();
        long total = asLong(summary, "total");
        long completed = asLong(summary, "completed");
        long failed = asLong(summary, "failed");
        long retried = asLong(summary, "retried");
        long finished = completed + failed;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptTotal", total);
        result.put("completedAttempts", completed);
        result.put("failedAttempts", failed);
        result.put("retriedAttempts", retried);
        result.put("successRate", finished == 0 ? null : round((double) completed / finished));
        result.put("retryRate", total == 0 ? null : round((double) retried / total));
        result.put("avgDurationMs", asLong(summary, "avgMs"));
        result.put("maxDurationMs", asLong(summary, "maxMs"));
        result.put("p95DurationMs", attemptMapper.p95DurationMs());

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("queueDepth", transcodeService.queueSize());
        runtime.put("running", transcodeService.activeCount());
        runtime.put("unfinishedTasks", progressMapper.countUnfinished());
        result.put("runtime", runtime);
        result.put("taskStatus", progressMapper.countByStatus());
        return result;
    }

    private long asLong(Map<String, Object> summary, String key) {
        if (summary == null) return 0L;
        Object value = summary.get(key);
        if (value instanceof Number number) return number.longValue();
        return 0L;
    }

    private double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
