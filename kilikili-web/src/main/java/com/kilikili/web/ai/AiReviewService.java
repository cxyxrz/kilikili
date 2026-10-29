package com.kilikili.web.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kilikili.web.service.RecommendationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 初审流水线：定时捞取待审核视频，调用 DeepSeek 给出审核结论并写入 video_audit。
 * 判定 PASS 且开启 auto-publish 时自动发布，其余情况保留待人工复核。
 */
@Service
public class AiReviewService {
    private static final Logger log = LoggerFactory.getLogger(AiReviewService.class);
    private static final String PASS = "PASS";
    private static final String REVIEW = "REVIEW";
    private static final String REJECT = "REJECT";
    private static final String ERROR = "ERROR";

    private final AiReviewProperties properties;
    private final AiReviewMapper aiReviewMapper;
    private final DeepSeekClient deepSeekClient;
    private final RecommendationService recommendationService;
    private final com.kilikili.web.search.VideoSearchIndexService searchIndexService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean missingKeyLogged = false;

    public AiReviewService(AiReviewProperties properties, AiReviewMapper aiReviewMapper, DeepSeekClient deepSeekClient,
                           RecommendationService recommendationService,
                           com.kilikili.web.search.VideoSearchIndexService searchIndexService) {
        this.properties = properties;
        this.aiReviewMapper = aiReviewMapper;
        this.deepSeekClient = deepSeekClient;
        this.recommendationService = recommendationService;
        this.searchIndexService = searchIndexService;
    }

    @Scheduled(initialDelayString = "${kilikili.ai.initial-delay-ms:10000}",
            fixedDelayString = "${kilikili.ai.poll-interval-ms:20000}")
    public void poll() {
        if (!properties.enabled() || !deepSeekClient.available()) {
            if (!missingKeyLogged) {
                log.warn("AI 审核未启用：请设置环境变量 DEEPSEEK_API_KEY（或 kilikili.ai.enabled=false 关闭）");
                missingKeyLogged = true;
            }
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        try {
            List<AiReviewSource> sources = aiReviewMapper.pendingSources(Math.max(1, properties.batchSize()));
            for (AiReviewSource source : sources) reviewOne(source);
        } catch (Exception e) {
            log.error("AI 审核轮询异常", e);
        } finally {
            running.set(false);
        }
    }

    private void reviewOne(AiReviewSource source) {
        try {
            DeepSeekClient.Verdict verdict = deepSeekClient.review(source);
            aiReviewMapper.insertAiAudit(source.id(), verdict.result(), verdict.reason(), verdict.score(), verdict.detail());
            boolean published = decide(source.id(), verdict);
            log.info("AI 审核完成: videoId={}, result={}, score={}, 自动发布={}", source.id(), verdict.result(), verdict.score(), published);
        } catch (Exception e) {
            // 记录失败结果，避免同一条视频被无限重试；管理员仍可在后台人工审核
            String reason = "AI 审核调用失败：" + e.getMessage();
            aiReviewMapper.insertAiAudit(source.id(), ERROR, reason.length() > 480 ? reason.substring(0, 480) : reason, null, null);
            log.error("AI 审核失败: videoId={}", source.id(), e);
        }
    }

    private boolean decide(long videoId, DeepSeekClient.Verdict verdict) {
        if (!properties.autoPublish() || !PASS.equals(verdict.result())) return false;
        boolean published = aiReviewMapper.updatePendingStatus(videoId, "PUBLISHED") > 0;
        if (published) {
            recommendationService.evictRecommendCache();
            searchIndexService.indexVideo(videoId);
        }
        return published;
    }
}
