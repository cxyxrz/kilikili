package com.kilikili.web.mq;

import com.kilikili.web.entity.Video;
import com.kilikili.web.mapper.VideoMapper;
import com.kilikili.web.search.VideoSearchIndexService;
import com.kilikili.web.service.NotificationService;
import com.kilikili.web.service.RecommendationService;
import com.kilikili.web.transcode.TranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 各阶段的实际业务动作。方法必须做到「可重复执行」：
 * 只有当前阶段不是 DONE 时才执行，成功后标记 DONE 并投递下一阶段。
 */
@Service
public class PipelineStageExecutor {
    private static final Logger log = LoggerFactory.getLogger(PipelineStageExecutor.class);
    private static final String DONE = "DONE";
    private static final String FAILED = "FAILED";
    private static final String WAITING = "WAITING";
    private static final AtomicInteger executed = new AtomicInteger();

    private final VideoPipelineMapper pipelineMapper;
    private final VideoPipelineService pipelineService;
    private final VideoMapper videoMapper;
    private final TranscodeService transcodeService;
    private final NotificationService notificationService;
    private final RecommendationService recommendationService;
    private final VideoSearchIndexService searchIndexService;

    public PipelineStageExecutor(VideoPipelineMapper pipelineMapper, VideoPipelineService pipelineService,
                                 VideoMapper videoMapper, TranscodeService transcodeService,
                                 NotificationService notificationService, RecommendationService recommendationService,
                                 VideoSearchIndexService searchIndexService) {
        this.pipelineMapper = pipelineMapper;
        this.pipelineService = pipelineService;
        this.videoMapper = videoMapper;
        this.transcodeService = transcodeService;
        this.notificationService = notificationService;
        this.recommendationService = recommendationService;
        this.searchIndexService = searchIndexService;
    }

    public static int executedCount() {
        return executed.get();
    }

    public boolean execute(PipelineStage stage, long videoId) {
        if (DONE.equals(pipelineMapper.findStatus(videoId, stage.name()))) {
            log.info("阶段已完成，跳过重复消费: videoId={}, stage={}", videoId, stage);
            return true;
        }
        long startedAt = System.currentTimeMillis();
        try {
            boolean advance = switch (stage) {
                case REVIEW -> review(videoId);
                case TRANSCODE -> transcode(videoId);
                case NOTIFY -> notifyUser(videoId);
                case INDEX -> index(videoId);
            };
            executed.incrementAndGet();
            log.info("流水线阶段完成: videoId={}, stage={}, 耗时={}ms, 继续下一阶段={}",
                    videoId, stage, System.currentTimeMillis() - startedAt, advance);
            if (advance) {
                PipelineStage next = stage.next();
                if (next != null) pipelineService.publish(next, videoId);
            }
            return true;
        } catch (Exception exception) {
            String message = truncate(friendly(exception), 480);
            pipelineMapper.updateStatus(videoId, stage.name(), FAILED, message);
            log.error("流水线阶段失败: videoId={}, stage={}, error={}", videoId, stage, message);
            throw new IllegalStateException(message, exception);
        }
    }

    /** 审核阶段：待审核的视频在此等待人工/AI 结果，不会阻塞其他视频 */
    private boolean review(long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) throw new IllegalArgumentException("视频不存在: " + videoId);
        if ("PENDING_REVIEW".equals(video.status()) || "DRAFT".equals(video.status())) {
            pipelineMapper.updateStatus(videoId, PipelineStage.REVIEW.name(), WAITING, null);
            log.info("视频待审核，流水线挂起等待审核结果: videoId={}, status={}", videoId, video.status());
            return false;
        }
        if ("PUBLISHED".equals(video.status())) {
            pipelineMapper.updateStatus(videoId, PipelineStage.REVIEW.name(), DONE, null);
            return true;
        }
        // 驳回/下架：终止后续阶段
        pipelineMapper.updateStatus(videoId, PipelineStage.REVIEW.name(), DONE, null);
        log.info("视频未通过审核，终止流水线: videoId={}, status={}", videoId, video.status());
        return false;
    }

    private boolean transcode(long videoId) {
        transcodeService.submit(videoId);
        pipelineMapper.updateStatus(videoId, PipelineStage.TRANSCODE.name(), DONE, null);
        return true;
    }

    private boolean notifyUser(long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) throw new IllegalArgumentException("视频不存在: " + videoId);
        notificationService.createSystem(video.authorId(), "您的投稿《" + video.title() + "》已进入转码队列，完成后可播放多清晰度",
                videoId);
        pipelineMapper.updateStatus(videoId, PipelineStage.NOTIFY.name(), DONE, null);
        return true;
    }

    private boolean index(long videoId) {
        recommendationService.evictRecommendCache();
        searchIndexService.indexVideoStrict(videoId);
        pipelineMapper.updateStatus(videoId, PipelineStage.INDEX.name(), DONE, null);
        return true;
    }

    private String friendly(Exception exception) {
        return exception.getMessage() == null ? exception.toString() : exception.getMessage();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
