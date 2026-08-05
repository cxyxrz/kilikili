package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.common.setting.SystemSettingService;
import com.kilikili.web.entity.FollowerItem;
import com.kilikili.web.entity.RecommendItem;
import com.kilikili.web.mapper.AccountMapper;
import com.kilikili.web.mapper.FollowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;

@Service
public class FollowService {
    private final FollowMapper followMapper;
    private final AccountMapper accountMapper;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final SystemSettingService systemSettingService;

    public FollowService(FollowMapper followMapper, AccountMapper accountMapper, NotificationService notificationService, StorageService storageService, SystemSettingService systemSettingService) {
        this.followMapper = followMapper;
        this.accountMapper = accountMapper;
        this.notificationService = notificationService;
        this.storageService = storageService;
        this.systemSettingService = systemSettingService;
    }

    @Transactional
    public Map<String, Object> toggle(long followerId, long followingId) {
        systemSettingService.requireEnabled(SystemSettingService.FOLLOW_ENABLED, "关注功能已关闭");
        if (followerId == followingId) throw new BusinessException("不能关注自己");
        if (accountMapper.findById(followingId) == null) throw new BusinessException("UP 主不存在");
        if (followMapper.hasFollowed(followerId, followingId) > 0) {
            followMapper.delete(followerId, followingId);
            return Map.of("following", false);
        }
        followMapper.insert(followerId, followingId);
        notificationService.create(followingId, followerId, "FOLLOW", "关注了你", null);
        return Map.of("following", true);
    }

    public boolean isFollowing(long followerId, long followingId) {
        return followMapper.hasFollowed(followerId, followingId) > 0;
    }

    public Map<String, Object> followers(long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<FollowerItem> items = followMapper.listFollowers(userId, pageSize, offset).stream()
                .map(item -> new FollowerItem(item.id(), item.nickname(), item.avatar() == null || item.avatar().isBlank()
                        ? null : storageService.presignedReadUrl(item.avatar()), item.followedAt()))
                .toList();
        long total = followMapper.countFollowers(userId);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total,
                "hasMore", (long) page * pageSize < total);
    }

    public Map<String, Object> followingVideos(long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<RecommendItem> items = followMapper.listFollowingVideos(userId, pageSize, offset).stream()
                .map(item -> new RecommendItem(item.id(), item.title(), item.authorName(),
                        item.coverObjectKey() == null || item.coverObjectKey().isBlank() ? null : storageService.presignedReadUrl(item.coverObjectKey()),
                        item.durationSeconds(), item.viewCount(), item.likeCount(), item.commentCount(), item.categoryId()))
                .toList();
        long total = followMapper.countFollowingVideos(userId);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total,
                "hasMore", (long) page * pageSize < total);
    }
}
