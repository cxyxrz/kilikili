package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.common.setting.SystemSettingService;
import com.kilikili.web.entity.TranscodeTask;
import com.kilikili.web.entity.TranscodeTaskProgress;
import com.kilikili.web.entity.Video;
import com.kilikili.web.entity.VideoComment;
import com.kilikili.web.entity.VideoInsert;
import com.kilikili.web.entity.CreatorVideoItem;
import com.kilikili.web.entity.CreatorVideoSource;
import com.kilikili.web.entity.HistoryVideoItem;
import com.kilikili.web.entity.HistoryVideoSource;
import com.kilikili.web.entity.Danmaku;
import com.kilikili.web.entity.FavoriteVideoItem;
import com.kilikili.web.entity.FavoriteVideoSource;
import com.kilikili.web.mapper.TranscodeTaskMapper;
import com.kilikili.web.mapper.TranscodeProgressMapper;
import com.kilikili.web.mapper.VideoCleanupMapper;
import com.kilikili.web.mapper.VideoMapper;
import com.kilikili.web.transcode.TranscodeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoService.class);
    private static final long UPLOAD_SESSION_TTL_SECONDS = 3600;
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final SystemSettingService systemSettingService;
    private final TranscodeTaskMapper transcodeTaskMapper;
    private final TranscodeProgressMapper transcodeProgressMapper;
    private final TranscodeService transcodeService;
    private final RecommendationService recommendationService;
    private final com.kilikili.web.search.VideoSearchIndexService searchIndexService;
    private final com.kilikili.web.mq.VideoPipelineService pipelineService;
    private final com.kilikili.web.mapper.DanmakuInsightMapper danmakuInsightMapper;
    private final DanmakuModerationService danmakuModerationService;
    private final VideoCleanupMapper videoCleanupMapper;
    private final ConcurrentHashMap<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();

    private record UploadSession(long userId, String objectKey, String contentType,
                                 ConcurrentHashMap<Integer, String> partKeys,
                                 Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(UPLOAD_SESSION_TTL_SECONDS));
        }
    }

    public VideoService(VideoMapper videoMapper, StorageService storageService, NotificationService notificationService,
                        SystemSettingService systemSettingService, TranscodeTaskMapper transcodeTaskMapper,
                        TranscodeProgressMapper transcodeProgressMapper,
                        TranscodeService transcodeService, RecommendationService recommendationService,
                        com.kilikili.web.search.VideoSearchIndexService searchIndexService,
                        com.kilikili.web.mq.VideoPipelineService pipelineService,
                        com.kilikili.web.mapper.DanmakuInsightMapper danmakuInsightMapper,
                        DanmakuModerationService danmakuModerationService,
                        VideoCleanupMapper videoCleanupMapper) {
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.systemSettingService = systemSettingService;
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.transcodeProgressMapper = transcodeProgressMapper;
        this.transcodeService = transcodeService;
        this.recommendationService = recommendationService;
        this.searchIndexService = searchIndexService;
        this.pipelineService = pipelineService;
        this.danmakuInsightMapper = danmakuInsightMapper;
        this.danmakuModerationService = danmakuModerationService;
        this.videoCleanupMapper = videoCleanupMapper;
    }

    @Transactional
    public Map<String, Object> upload(long userId, String title, String description, long categoryId, int durationSeconds, String creationDeclaration, MultipartFile cover, String aiCoverObjectKey, MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException("请选择视频文件");
        if (file.getSize() > 16L * 1024 * 1024 * 1024) throw new BusinessException("视频大小不能超过 16GB");
        if (file.getContentType() == null || !file.getContentType().startsWith("video/")) throw new BusinessException("仅支持视频文件");
        if (durationSeconds < 0 || durationSeconds > 24 * 60 * 60) throw new BusinessException("视频时长不合法");
        if (!("ORIGINAL".equals(creationDeclaration) || "REPRINT".equals(creationDeclaration))) throw new BusinessException("创作声明不合法");
        String objectKey = storageService.uploadVideo(file, userId);
        String coverObjectKey = resolveCover(userId, cover, aiCoverObjectKey);
        boolean reviewEnabled = systemSettingService.isEnabled(SystemSettingService.VIDEO_REVIEW_ENABLED);
        String status = reviewEnabled ? "PENDING_REVIEW" : "PUBLISHED";
        VideoInsert insert = new VideoInsert(userId, title, description, objectKey, coverObjectKey, categoryId, durationSeconds, creationDeclaration, status);
        videoMapper.insertReturningId(insert);
        // 投稿成功后由 MQ 流水线推进：审核 → 转码 → 通知 → 建索引
        pipelineService.start(insert.getId());
        log.info("视频投稿已创建: videoId={}, userId={}, title={}, status={}", insert.getId(), userId, title, status);
        return Map.of("message", reviewEnabled ? "上传成功，正在等待审核，审核通过后自动转码" : "上传成功，已自动发布，后台转码中", "status", status, "videoId", insert.getId());
    }

    public Map<String, Object> initUpload(long userId, String filename, String contentType) {
        if (contentType == null || !contentType.startsWith("video/")) throw new BusinessException("仅支持视频文件");
        String objectKey = storageService.generateVideoObjectKey(userId, filename);
        String sessionId = UUID.randomUUID().toString();
        uploadSessions.put(sessionId, new UploadSession(userId, objectKey, contentType, new ConcurrentHashMap<>(), Instant.now()));
        log.info("分片上传已初始化: userId={}, sessionId={}, objectKey={}", userId, sessionId, objectKey);
        return Map.of("sessionId", sessionId, "objectKey", objectKey);
    }

    public Map<String, Object> uploadChunk(long userId, String sessionId, int partNumber, MultipartFile chunk) {
        UploadSession session = requireUploadSession(sessionId, userId);
        if (session.isExpired()) {
            uploadSessions.remove(sessionId);
            throw new BusinessException("上传会话已过期，请重新初始化");
        }
        String partKey = storageService.uploadChunk(session.objectKey(), partNumber, chunk);
        session.partKeys().put(partNumber, partKey);
        log.info("分片已接收: userId={}, sessionId={}, part={}/{}, size={}", userId, sessionId, partNumber, session.partKeys().size(), chunk.getSize());
        return Map.of("partNumber", partNumber);
    }

    @Transactional
    public Map<String, Object> completeUpload(long userId, String sessionId,
                                              String title, String description,
                                              long categoryId, int durationSeconds,
                                              String creationDeclaration,
                                              MultipartFile cover, String aiCoverObjectKey) {
        UploadSession session = requireUploadSession(sessionId, userId);
        if (session.partKeys().isEmpty()) throw new BusinessException("未上传任何分片");
        if (!("ORIGINAL".equals(creationDeclaration) || "REPRINT".equals(creationDeclaration))) throw new BusinessException("创作声明不合法");
        if (durationSeconds < 0 || durationSeconds > 24 * 60 * 60) throw new BusinessException("视频时长不合法");

        List<String> sortedPartKeys = session.partKeys().keySet().stream()
                .sorted()
                .map(key -> session.partKeys().get(key))
                .toList();
        storageService.composeChunks(session.objectKey(), sortedPartKeys, session.contentType());
        uploadSessions.remove(sessionId);

        String coverObjectKey = resolveCover(userId, cover, aiCoverObjectKey);
        boolean reviewEnabled = systemSettingService.isEnabled(SystemSettingService.VIDEO_REVIEW_ENABLED);
        String status = reviewEnabled ? "PENDING_REVIEW" : "PUBLISHED";
        VideoInsert insert = new VideoInsert(userId, title, description, session.objectKey(), coverObjectKey, categoryId, durationSeconds, creationDeclaration, status);
        videoMapper.insertReturningId(insert);
        pipelineService.start(insert.getId());
        log.info("分片上传视频投稿已创建: videoId={}, userId={}, title={}, status={}, parts={}", insert.getId(), userId, title, status, sortedPartKeys.size());
        return Map.of("message", reviewEnabled ? "上传成功，正在等待审核，审核通过后自动转码" : "上传成功，已自动发布，后台转码中", "status", status, "videoId", insert.getId());
    }

    /**
     * 封面来源二选一：AI 封面直接复用已上传到 MinIO 的对象（限定本人目录），否则使用上传的封面文件。
     */
    private String resolveCover(long userId, MultipartFile cover, String aiCoverObjectKey) {
        if (aiCoverObjectKey != null && !aiCoverObjectKey.isBlank()) {
            String prefix = "covers/" + userId + "/";
            if (!aiCoverObjectKey.startsWith(prefix)) throw new BusinessException("封面来源不合法");
            return aiCoverObjectKey;
        }
        if (cover == null || cover.isEmpty()) throw new BusinessException("请上传封面");
        return storageService.uploadCover(cover, userId);
    }

    private UploadSession requireUploadSession(String sessionId, long userId) {
        UploadSession session = uploadSessions.get(sessionId);
        if (session == null) throw new BusinessException("上传会话不存在");
        if (session.userId() != userId) throw new BusinessException("无权操作此上传会话");
        return session;
    }

    public List<Video> list() { return videoMapper.listPublished(30).stream().map(this::displayVideo).toList(); }

    public Map<String, Object> detail(long id) {
        Video video = requireVideo(id);
        if (!"PUBLISHED".equals(video.status())) throw new BusinessException("视频暂不可播放");
        videoMapper.increaseViews(id);
        log.info("视频开始播放: videoId={}, authorId={}", id, video.authorId());
        TranscodeTask task = transcodeTaskMapper.findLatestByVideoId(id);
        Map<String, Object> transcodeInfo = new java.util.LinkedHashMap<>();
        String playUrl;
        if (task != null && "COMPLETED".equals(task.status()) && task.hlsPlaylistKey() != null) {
            playUrl = "/api/videos/" + id + "/stream/master.m3u8";
            transcodeInfo.put("status", "READY");
            transcodeInfo.put("renditions", task.renditions() == null ? List.of() : List.of(task.renditions().split(",")));
            transcodeInfo.put("percent", 100);
            transcodeInfo.put("stage", "COMPLETED");
        } else {
            playUrl = storageService.presignedReadUrl(video.objectKey());
            transcodeInfo.put("status", task == null ? "NONE" : task.status());
            if (task != null) {
                TranscodeTaskProgress progress = transcodeProgressMapper.findByTaskId(task.id());
                transcodeInfo.put("percent", progress == null ? 0 : progress.percent());
                transcodeInfo.put("stage", progress == null ? task.status() : progress.stage());
                transcodeInfo.put("message", progress == null ? null : progress.message());
                transcodeInfo.put("attempt", task.attemptCount());
                transcodeInfo.put("maxAttempts", task.maxAttempts());
            }
        }
        return Map.of("video", displayVideo(video), "playUrl", playUrl, "transcode", transcodeInfo);
    }

    public String masterPlaylist(long videoId) {
        TranscodeTask task = requireReadyTask(videoId);
        return storageService.readTextObject(task.hlsPlaylistKey());
    }

    public String renditionPlaylist(long videoId, String rendition) {
        TranscodeTask task = requireReadyTask(videoId);
        requireRendition(task, rendition);
        String playlistKey = task.hlsPlaylistKey();
        return storageService.readTextObject(playlistKey.substring(0, playlistKey.lastIndexOf('/') + 1) + rendition + "/index.m3u8");
    }

    public String segmentUrl(long videoId, String rendition, String segment) {
        if (!segment.matches("seg_\\d{5}\\.ts")) throw new BusinessException("非法的切片名称");
        TranscodeTask task = requireReadyTask(videoId);
        requireRendition(task, rendition);
        String playlistKey = task.hlsPlaylistKey();
        String prefix = playlistKey.substring(0, playlistKey.lastIndexOf('/') + 1);
        return storageService.presignedReadUrl(prefix + rendition + "/" + segment);
    }

    @Transactional
    public Map<String, Object> retranscode(long userId, long videoId) {
        Video video = requireVideo(videoId);
        if (video.authorId() != userId) throw new BusinessException("仅视频作者可重新转码");
        transcodeService.submit(videoId);
        return Map.of("message", "转码任务已提交");
    }

    private TranscodeTask requireReadyTask(long videoId) {
        Video video = requireVideo(videoId);
        if (!"PUBLISHED".equals(video.status())) throw new BusinessException("视频暂不可播放");
        TranscodeTask task = transcodeTaskMapper.findLatestByVideoId(videoId);
        if (task == null || !"COMPLETED".equals(task.status()) || task.hlsPlaylistKey() == null)
            throw new BusinessException("视频尚未完成转码");
        return task;
    }

    private void requireRendition(TranscodeTask task, String rendition) {
        List<String> available = task.renditions() == null ? List.of() : List.of(task.renditions().split(","));
        if (!available.contains(rendition)) throw new BusinessException("不支持的清晰度: " + rendition);
    }

    @Transactional
    public Map<String, Object> toggleLike(long userId, long videoId) {
        systemSettingService.requireEnabled(SystemSettingService.LIKE_ENABLED, "点赞功能已关闭");
        Video video = requireVideo(videoId);
        if (videoMapper.hasLiked(userId, videoId) > 0) {
            videoMapper.deleteLike(userId, videoId);
            videoMapper.changeLikeCount(videoId, -1);
            log.info("取消视频点赞: userId={}, videoId={}", userId, videoId);
            return Map.of("liked", false);
        }
        videoMapper.addLike(userId, videoId);
        videoMapper.changeLikeCount(videoId, 1);
        notificationService.create(video.authorId(), userId, "LIKE", "赞了你的视频《" + video.title() + "》", videoId);
        log.info("视频点赞成功: userId={}, videoId={}", userId, videoId);
        return Map.of("liked", true);
    }

    @Transactional
    public Map<String, Object> toggleFavorite(long userId, long videoId) {
        systemSettingService.requireEnabled(SystemSettingService.FAVORITE_ENABLED, "收藏功能已关闭");
        requireVideo(videoId);
        if (videoMapper.hasFavorited(userId, videoId) > 0) {
            videoMapper.deleteFavorite(userId, videoId);
            log.info("取消视频收藏: userId={}, videoId={}", userId, videoId);
            return Map.of("favorited", false);
        }
        videoMapper.addFavorite(userId, videoId);
        log.info("视频收藏成功: userId={}, videoId={}", userId, videoId);
        return Map.of("favorited", true);
    }

    public boolean isFavorited(long userId, long videoId) { return videoMapper.hasFavorited(userId, videoId) > 0; }

    public Map<String, Object> favorites(long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<FavoriteVideoItem> items = videoMapper.listFavorites(userId, pageSize, offset).stream()
                .map(source -> new FavoriteVideoItem(source.id(), source.title(), source.authorName(), favoriteCoverUrl(source),
                        source.durationSeconds(), source.viewCount(), source.likeCount(), source.commentCount(), source.favoritedAt()))
                .toList();
        long total = videoMapper.countFavorites(userId);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total, "hasMore", (long) page * pageSize < total);
    }

    @Transactional
    public void comment(long userId, long videoId, String content) {
        systemSettingService.requireEnabled(SystemSettingService.COMMENT_ENABLED, "评论功能已关闭");
        Video video = requireVideo(videoId);
        videoMapper.insertComment(new VideoComment(null, videoId, userId, null, content, null, null, null));
        videoMapper.increaseComments(videoId);
        notificationService.create(video.authorId(), userId, "COMMENT", "评论了你：" + content, videoId);
        log.info("视频评论已发布: userId={}, videoId={}, contentLength={}", userId, videoId, content.length());
    }
    @Transactional
    public void reply(long userId, long videoId, long commentId, String content) {
        systemSettingService.requireEnabled(SystemSettingService.COMMENT_ENABLED, "评论功能已关闭");
        requireVideo(videoId);
        VideoComment parent = videoMapper.listComments(videoId).stream().filter(item -> item.id().equals(commentId)).findFirst()
                .orElseThrow(() -> new BusinessException("要回复的评论不存在"));
        long rootCommentId = parent.parentId() == null ? parent.id() : parent.parentId();
        videoMapper.insertReply(videoId, userId, rootCommentId, content);
        videoMapper.increaseComments(videoId);
        notificationService.create(parent.userId(), userId, "COMMENT", "回复了你：" + content, videoId);
        log.info("视频评论回复已发布: userId={}, videoId={}, parentId={}, contentLength={}", userId, videoId, rootCommentId, content.length());
    }
    public List<VideoComment> comments(long videoId) {
        requireVideo(videoId);
        return videoMapper.listComments(videoId).stream()
                .map(comment -> new VideoComment(comment.id(), comment.videoId(), comment.userId(), comment.parentId(), comment.content(),
                        comment.createdAt(), comment.nickname(), comment.avatar() == null || comment.avatar().isBlank()
                        ? null : storageService.presignedReadUrl(comment.avatar())))
                .toList();
    }
    public Map<String, Object> mine(long userId, String keyword, String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<CreatorVideoItem> items = videoMapper.listMine(userId, keyword, status, pageSize, offset).stream()
                .map(source -> new CreatorVideoItem(source.id(), source.title(), source.coverObjectKey() == null || source.coverObjectKey().isBlank() ? null : storageService.presignedReadUrl(source.coverObjectKey()), source.status(), source.viewCount(), source.likeCount(), source.commentCount(), source.createdAt())).toList();
        long total = videoMapper.countMine(userId, keyword, status);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total, "hasMore", (long) page * pageSize < total);
    }
    public Map<String, Object> creatorStats(long userId) { return videoMapper.creatorStats(userId); }

    /**
     * 删除视频及其全部关联数据：数据库按 video_id 逐表清理，事务提交后再清理对象存储、ES 索引与推荐缓存。
     * 仅作者本人可删除。
     */
    @Transactional
    public void deleteVideo(long userId, long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) throw new BusinessException("视频不存在");
        if (video.authorId() != userId) throw new BusinessException("只能删除自己发布的视频");
        videoCleanupMapper.deleteComments(videoId);
        videoCleanupMapper.deleteDanmakus(videoId);
        videoCleanupMapper.deleteLikes(videoId);
        videoCleanupMapper.deleteFavorites(videoId);
        videoCleanupMapper.deleteViewHistory(videoId);
        videoCleanupMapper.deletePlayProgress(videoId);
        videoCleanupMapper.deleteDanmakuAnalysis(videoId);
        videoCleanupMapper.deleteDanmakuMasks(videoId);
        videoCleanupMapper.deleteAudits(videoId);
        videoCleanupMapper.deleteTranscodeAttempts(videoId);
        videoCleanupMapper.deleteTranscodeProgress(videoId);
        videoCleanupMapper.deleteTranscodeTasks(videoId);
        videoCleanupMapper.deletePipeline(videoId);
        videoCleanupMapper.deleteConsumeLogs(videoId);
        videoCleanupMapper.deleteDeadLetters(videoId);
        videoCleanupMapper.deleteNotifications(videoId);
        videoCleanupMapper.deleteVideo(videoId);
        log.info("视频关联数据已删除: videoId={}, authorId={}, title={}", videoId, userId, video.title());

        Runnable externalCleanup = () -> {
            searchIndexService.deleteVideo(videoId);
            recommendationService.evictRecommendCache();
            storageService.deleteObject(video.objectKey());
            storageService.deleteObject(video.coverObjectKey());
            String base = video.objectKey().contains(".")
                    ? video.objectKey().substring(0, video.objectKey().lastIndexOf('.')) : video.objectKey();
            storageService.deletePrefix(base + "/hls");
        };
        // 外部资源只能在事务提交后清理，避免回滚时索引与文件已被删除
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { externalCleanup.run(); }
            });
        } else {
            externalCleanup.run();
        }
    }

    @Transactional
    public void recordHistory(long userId, long videoId) {
        Video video = requireVideo(videoId);
        if (!"PUBLISHED".equals(video.status())) throw new BusinessException("仅已发布视频可记录观看历史");
        videoMapper.saveHistory(userId, videoId);
        log.info("观看历史已记录: userId={}, videoId={}", userId, videoId);
    }

    public Map<String, Object> history(long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<HistoryVideoItem> items = videoMapper.listHistory(userId, pageSize, offset).stream()
                .map(source -> new HistoryVideoItem(source.id(), source.title(), source.authorName(), historyCoverUrl(source),
                        source.durationSeconds(), source.viewCount(), source.likeCount(), source.commentCount(), source.watchedAt()))
                .toList();
        long total = videoMapper.countHistory(userId);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total, "hasMore", (long) page * pageSize < total);
    }

    @Transactional
    public void deleteHistory(long userId, long videoId) {
        videoMapper.deleteHistory(userId, videoId);
        log.info("观看历史已删除: userId={}, videoId={}", userId, videoId);
    }

    @Transactional
    public void clearHistory(long userId) {
        videoMapper.clearHistory(userId);
        log.info("观看历史已清空: userId={}", userId);
    }

    public List<Danmaku> danmakus(long videoId) {
        requireVideo(videoId);
        return videoMapper.listDanmakus(videoId);
    }

    /** 弹幕热度图：按固定秒数分桶聚合，返回每个时间桶的弹幕条数与峰值位置 */
    public Map<String, Object> danmakuHeatmap(long videoId, int bucketSeconds) {
        Video video = requireVideo(videoId);
        int bucket = Math.max(5, Math.min(60, bucketSeconds));
        long bucketMs = bucket * 1000L;
        List<com.kilikili.web.entity.DanmakuHeatBucket> rows = danmakuInsightMapper.heatBuckets(videoId, bucketMs);
        long total = 0L;
        long maxCount = 0L;
        long peakBucket = -1L;
        for (com.kilikili.web.entity.DanmakuHeatBucket row : rows) {
            total += row.count();
            if (row.count() > maxCount) {
                maxCount = row.count();
                peakBucket = row.bucket();
            }
        }
        long durationMs = video.durationSeconds() == null ? 0L : video.durationSeconds() * 1000L;
        if (!rows.isEmpty()) {
            durationMs = Math.max(durationMs, (rows.get(rows.size() - 1).bucket() + 1) * bucketMs);
        }
        List<Map<String, Object>> buckets = rows.stream()
                .map(row -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<String, Object>();
                    item.put("startMs", row.bucket() * bucketMs);
                    item.put("endMs", (row.bucket() + 1) * bucketMs);
                    item.put("count", row.count());
                    return item;
                })
                .toList();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("bucketSeconds", bucket);
        result.put("durationMs", durationMs);
        result.put("totalCount", total);
        result.put("maxCount", maxCount);
        result.put("peakMs", peakBucket < 0 ? 0L : peakBucket * bucketMs);
        result.put("buckets", buckets);
        return result;
    }

    @Transactional
    public void sendDanmaku(long userId, long videoId, String content, String mode, String color, int timeMs) {
        systemSettingService.requireEnabled(SystemSettingService.DANMAKU_ENABLED, "弹幕功能已关闭");
        Video video = requireVideo(videoId);
        if (!"PUBLISHED".equals(video.status())) throw new BusinessException("仅已发布视频可以发送弹幕");
        danmakuModerationService.check(userId, videoId, content.trim());
        videoMapper.insertDanmaku(new Danmaku(null, videoId, userId, content.trim(), mode, color.toUpperCase(), timeMs, null));
        log.info("视频弹幕已发布: userId={}, videoId={}, mode={}, timeMs={}", userId, videoId, mode, timeMs);
    }

    private Video displayVideo(Video video) {
        String authorAvatar = video.authorAvatar() == null || video.authorAvatar().isBlank()
                ? null : storageService.presignedReadUrl(video.authorAvatar());
        return new Video(video.id(), video.authorId(), video.title(), video.description(), video.objectKey(), video.coverUrl(),
                video.coverObjectKey(), video.categoryId(), video.durationSeconds(), video.creationDeclaration(), video.status(),
                video.viewCount(), video.likeCount(), video.commentCount(), video.createdAt(), video.authorName(), authorAvatar);
    }
    private String historyCoverUrl(HistoryVideoSource source) {
        return source.coverObjectKey() == null || source.coverObjectKey().isBlank()
                ? source.coverUrl() : storageService.presignedReadUrl(source.coverObjectKey());
    }
    private String favoriteCoverUrl(FavoriteVideoSource source) {
        return source.coverObjectKey() == null || source.coverObjectKey().isBlank()
                ? source.coverUrl() : storageService.presignedReadUrl(source.coverObjectKey());
    }
    private Video requireVideo(long id) { Video video = videoMapper.findById(id); if (video == null) throw new BusinessException("视频不存在"); return video; }
}