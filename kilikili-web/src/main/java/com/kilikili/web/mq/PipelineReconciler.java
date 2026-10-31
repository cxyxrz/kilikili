package com.kilikili.web.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 最终一致补偿：阶段长时间未完成（消息丢失、消费失败后状态未推进）时重新投递。
 * 属于兜底路径，正常链路上不会触发。
 */
@Component
public class PipelineReconciler {
    private static final Logger log = LoggerFactory.getLogger(PipelineReconciler.class);

    private final VideoPipelineMapper pipelineMapper;
    private final VideoPipelineService pipelineService;
    private final MqProperties properties;

    public PipelineReconciler(VideoPipelineMapper pipelineMapper, VideoPipelineService pipelineService,
                              MqProperties properties) {
        this.pipelineMapper = pipelineMapper;
        this.pipelineService = pipelineService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${kilikili.mq.reconcile-interval-ms:60000}")
    public void reconcile() {
        if (!properties.enabled()) return;
        List<Map<String, Object>> stuck = pipelineMapper.findUnfinished(properties.reconcileDelaySeconds(), 20);
        for (Map<String, Object> row : stuck) {
            long videoId = ((Number) row.get("video_id")).longValue();
            String stage = String.valueOf(row.get("stage"));
            pipelineService.publish(PipelineStage.valueOf(stage), videoId);
            log.warn("补偿重投流水线阶段: videoId={}, stage={}", videoId, stage);
        }
        if (!stuck.isEmpty()) log.info("本轮补偿重投 {} 个阶段", stuck.size());
        resumeApproved();
    }

    /**
     * 审核结果的桥接：审核通过/驳回由管理端或 AI 直接改 video.status，
     * 这里把仍挂起在 REVIEW 的流水线唤醒，继续后续阶段。
     */
    private void resumeApproved() {
        for (Map<String, Object> row : pipelineMapper.findWaitingReview(50)) {
            long videoId = ((Number) row.get("video_id")).longValue();
            String status = String.valueOf(row.get("status"));
            if ("PUBLISHED".equals(status)) {
                pipelineMapper.updateStatus(videoId, PipelineStage.REVIEW.name(), "DONE", null);
                pipelineService.publish(PipelineStage.TRANSCODE, videoId);
                log.info("审核通过，恢复流水线: videoId={}", videoId);
            } else if (!"PENDING_REVIEW".equals(status) && !"DRAFT".equals(status)) {
                // 驳回/下架：审核阶段结束，不再推进
                pipelineMapper.updateStatus(videoId, PipelineStage.REVIEW.name(), "DONE", "审核未通过，终止流水线");
                log.info("审核未通过，终止流水线: videoId={}, status={}", videoId, status);
            }
        }
    }
}
