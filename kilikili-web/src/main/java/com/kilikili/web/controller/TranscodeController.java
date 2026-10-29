package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.entity.TranscodeTask;
import com.kilikili.web.entity.TranscodeTaskProgress;
import com.kilikili.web.mapper.TranscodeProgressMapper;
import com.kilikili.web.mapper.TranscodeTaskMapper;
import com.kilikili.web.transcode.TranscodeMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 转码进度与可观测性接口：前端轮询进度，运维查看成功率/耗时/队列积压。
 */
@Tag(name = "转码任务")
@RestController
public class TranscodeController {
    private final AuthService authService;
    private final TranscodeTaskMapper taskMapper;
    private final TranscodeProgressMapper progressMapper;
    private final TranscodeMetricsService metricsService;

    public TranscodeController(AuthService authService, TranscodeTaskMapper taskMapper,
                               TranscodeProgressMapper progressMapper, TranscodeMetricsService metricsService) {
        this.authService = authService;
        this.taskMapper = taskMapper;
        this.progressMapper = progressMapper;
        this.metricsService = metricsService;
    }

    @Operation(summary = "查询视频转码进度")
    @GetMapping("/api/videos/{id}/transcode/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable long id,
                                                     @RequestParam(defaultValue = "0") long taskId) {
        TranscodeTask task = taskId > 0 ? taskMapper.findById(taskId) : taskMapper.findLatestByVideoId(id);
        TranscodeTaskProgress progress = task == null
                ? progressMapper.findLatestByVideoId(id)
                : progressMapper.findByTaskId(task.id());
        Map<String, Object> result = new LinkedHashMap<>();
        if (task == null) {
            result.put("status", "NONE");
            result.put("percent", 0);
            result.put("stage", "NONE");
            return ApiResponse.ok(result);
        }
        result.put("taskId", task.id());
        result.put("videoId", task.videoId());
        result.put("status", task.status());
        result.put("attempt", task.attemptCount());
        result.put("maxAttempts", task.maxAttempts());
        result.put("error", task.errorMessage());
        result.put("renditions", task.renditions());
        result.put("percent", progress == null ? (isTerminal(task) ? 100 : 0) : progress.percent());
        result.put("stage", progress == null ? defaultStage(task) : progress.stage());
        result.put("fps", progress == null ? null : progress.fps());
        result.put("speed", progress == null ? null : progress.speed());
        result.put("etaSeconds", progress == null ? null : progress.etaSeconds());
        result.put("message", progress == null ? null : progress.message());
        result.put("updatedAt", progress == null ? null : progress.updatedAt());
        return ApiResponse.ok(result);
    }

    private boolean isTerminal(TranscodeTask task) {
        return "COMPLETED".equals(task.status());
    }

    /** 无进度快照时按任务状态给出一个前端可识别的阶段名 */
    private String defaultStage(TranscodeTask task) {
        return "PENDING".equals(task.status()) ? "QUEUED" : task.status();
    }

    @Operation(summary = "转码成功率与耗时统计", description = "成功率、平均/P95 耗时、重试率与队列积压")
    @GetMapping("/api/transcode/metrics")
    public ApiResponse<Map<String, Object>> metrics(@RequestHeader("Authorization") String authorization) {
        authService.requireUserId(authorization);
        return ApiResponse.ok(metricsService.metrics());
    }
}
