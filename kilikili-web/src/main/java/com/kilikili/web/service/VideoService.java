package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.common.setting.SystemSettingService;
import com.kilikili.web.entity.Video;
import com.kilikili.web.entity.VideoComment;
import com.kilikili.web.entity.CreatorVideoItem;
import com.kilikili.web.entity.CreatorVideoSource;
import com.kilikili.web.entity.HistoryVideoItem;
import com.kilikili.web.entity.HistoryVideoSource;
import com.kilikili.web.entity.Danmaku;
import com.kilikili.web.entity.FavoriteVideoItem;
import com.kilikili.web.entity.FavoriteVideoSource;
import com.kilikili.web.mapper.VideoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Service
public class VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoService.class);
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final SystemSettingService systemSettingService;
    public VideoService(VideoMapper videoMapper, StorageService storageService, NotificationService notificationService, SystemSettingService systemSettingService) {
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.systemSettingService = systemSettingService;
    }

    @Transactional
    public Map<String, Object> upload(long userId, String title, String description, long categoryId, int durationSeconds, String creationDeclaration, MultipartFile cover, MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException("请选择视频文件");
        if (file.getSize() > 16L * 1024 * 1024 * 1024) throw new BusinessException("视频大小不能超过 16GB");
        if (file.getContentType() == null || !file.getContentType().startsWith("video/")) throw new BusinessException("仅支持视频文件");
        if (durationSeconds < 0 || durationSeconds > 24 * 60 * 60) throw new BusinessException("视频时长不合法");
        if (!("ORIGINAL".equals(creationDeclaration) || "REPRINT".equals(creationDeclaration))) throw new BusinessException("创作声明不合法");
        if (cover == null || cover.isEmpty()) throw new BusinessException("请上传封面");
        String objectKey = storageService.uploadVideo(file, userId);
        String coverObjectKey = storageService.uploadCover(cover, userId);
        boolean reviewEnabled = systemSettingService.isEnabled(SystemSettingService.VIDEO_REVIEW_ENABLED);
        String status = reviewEnabled ? "PENDING_REVIEW" : "PUBLISHED";
        videoMapper.insert(userId, title, description, objectKey, coverObjectKey, categoryId, durationSeconds, creationDeclaration, status);
        log.info("视频投稿已创建: userId={}, title={}, status={}", userId, title, status);
        return Map.of("message", reviewEnabled ? "上传成功，正在等待审核" : "上传成功，已自动发布", "status", status);
    }

    public List<Video> list() { return videoMapper.listPublished(30).stream().map(this::displayVideo).toList(); }

    public Map<String, Object> detail(long id) {
        Video video = requireVideo(id);
        if (!"PUBLISHED".equals(video.status())) throw new BusinessException("视频暂不可播放");
        videoMapper.increaseViews(id);
        log.info("视频开始播放: videoId={}, authorId={}", id, video.authorId());
        return Map.of("video", displayVideo(video), "playUrl", storageService.presignedReadUrl(video.objectKey()));
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

    @Transactional
    public void sendDanmaku(long userId, long videoId, String content, String mode, String color, int timeMs) {
        systemSettingService.requireEnabled(SystemSettingService.DANMAKU_ENABLED, "弹幕功能已关闭");
        Video video = requireVideo(videoId);
        if (!"PUBLISHED".equals(video.status())) throw new BusinessException("仅已发布视频可以发送弹幕");
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
