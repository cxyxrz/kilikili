package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.mq.DeadLetter;
import com.kilikili.web.mq.MqConsumeLogMapper;
import com.kilikili.web.mq.MqDeadLetterMapper;
import com.kilikili.web.mq.MqProperties;
import com.kilikili.web.mq.PipelineConsumer;
import com.kilikili.web.mq.PipelineStage;
import com.kilikili.web.mq.VideoPipelineMapper;
import com.kilikili.web.mq.VideoPipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 投稿流水线运维接口：阶段状态、死信查看/回放、手动重试 */
@Tag(name = "消息流水线")
@RestController
public class MqAdminController {
    private final AuthService authService;
    private final MqProperties properties;
    private final VideoPipelineMapper pipelineMapper;
    private final MqDeadLetterMapper deadLetterMapper;
    private final MqConsumeLogMapper consumeLogMapper;
    private final VideoPipelineService pipelineService;
    private final PipelineConsumer consumer;

    public MqAdminController(AuthService authService, MqProperties properties, VideoPipelineMapper pipelineMapper,
                             MqDeadLetterMapper deadLetterMapper, MqConsumeLogMapper consumeLogMapper,
                             VideoPipelineService pipelineService, PipelineConsumer consumer) {
        this.authService = authService;
        this.properties = properties;
        this.pipelineMapper = pipelineMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.consumeLogMapper = consumeLogMapper;
        this.pipelineService = pipelineService;
        this.consumer = consumer;
    }

    @Operation(summary = "流水线运行状态")
    @GetMapping("/api/mq/metrics")
    public ApiResponse<Map<String, Object>> metrics(@RequestHeader("Authorization") String authorization) {
        authService.requireUserId(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mqEnabled", properties.enabled());
        result.put("nameServer", properties.namesrvAddr());
        result.put("topic", properties.topic());
        result.put("consumerRunning", consumer.running());
        result.put("maxReconsumeTimes", properties.maxReconsumeTimes());
        result.put("consumedMessages", consumeLogMapper.total());
        result.put("newDeadLetters", deadLetterMapper.countNew());
        result.put("stageStats", pipelineMapper.countByStageStatus());
        result.put("recent", pipelineMapper.recent(10));
        return ApiResponse.ok(result);
    }

    @Operation(summary = "死信列表")
    @GetMapping("/api/mq/dead-letters")
    public ApiResponse<List<DeadLetter>> deadLetters(@RequestHeader("Authorization") String authorization,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "20") int limit) {
        authService.requireUserId(authorization);
        return ApiResponse.ok(deadLetterMapper.list(status, Math.min(Math.max(limit, 1), 100)));
    }

    @Operation(summary = "回放死信", description = "按原阶段重新投递消息，成功后死信标记为 REPLAYED")
    @PostMapping("/api/mq/dead-letters/{id}/replay")
    public ApiResponse<Map<String, Object>> replay(@RequestHeader("Authorization") String authorization,
                                                   @PathVariable long id) {
        authService.requireUserId(authorization);
        DeadLetter deadLetter = deadLetterMapper.findById(id);
        if (deadLetter == null) return ApiResponse.fail(404, "死信不存在");
        if (deadLetter.stage() == null) return ApiResponse.fail(400, "死信缺少阶段信息，无法回放");
        boolean sent = pipelineService.publish(PipelineStage.valueOf(deadLetter.stage()), deadLetter.videoId());
        if (!sent) return ApiResponse.fail(500, "消息投递失败，请检查 MQ 状态");
        deadLetterMapper.markReplayed(id);
        return ApiResponse.ok(Map.of("message", "已重新投递", "videoId", deadLetter.videoId(),
                "stage", deadLetter.stage()));
    }

    @Operation(summary = "手动重试某视频的流水线阶段")
    @PostMapping("/api/mq/pipeline/{videoId}/retry")
    public ApiResponse<Map<String, Object>> retry(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable long videoId,
                                                  @RequestParam(defaultValue = "REVIEW") String stage) {
        authService.requireUserId(authorization);
        PipelineStage parsed;
        try {
            parsed = PipelineStage.valueOf(stage.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ApiResponse.fail(400, "不支持的阶段: " + stage);
        }
        boolean sent = pipelineService.publish(parsed, videoId);
        return sent ? ApiResponse.ok(Map.of("message", "已投递", "stage", parsed.name(), "videoId", videoId))
                : ApiResponse.fail(500, "消息投递失败，请检查 MQ 状态");
    }
}
