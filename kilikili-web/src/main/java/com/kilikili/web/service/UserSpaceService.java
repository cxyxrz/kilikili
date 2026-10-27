package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.AccountProfile;
import com.kilikili.web.entity.RecommendItem;
import com.kilikili.web.mapper.AccountMapper;
import com.kilikili.web.mapper.FollowMapper;
import com.kilikili.web.mapper.VideoMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UP 主个人主页（空间页）数据 */
@Service
public class UserSpaceService {
    private final AccountMapper accountMapper;
    private final FollowMapper followMapper;
    private final VideoMapper videoMapper;
    private final StorageService storageService;

    public UserSpaceService(AccountMapper accountMapper, FollowMapper followMapper, VideoMapper videoMapper,
                            StorageService storageService) {
        this.accountMapper = accountMapper;
        this.followMapper = followMapper;
        this.videoMapper = videoMapper;
        this.storageService = storageService;
    }

    public Map<String, Object> space(long userId, Long viewerId, String sort, int page, int pageSize) {
        AccountProfile profile = accountMapper.findProfileById(userId);
        if (profile == null) throw new BusinessException("用户不存在");
        Map<String, Object> stats = videoMapper.spaceStats(userId);
        int offset = (page - 1) * pageSize;
        List<RecommendItem> videos = videoMapper.listPublishedByAuthor(userId, sort, pageSize, offset).stream()
                .map(item -> new RecommendItem(item.id(), item.title(), item.authorName(),
                        item.coverObjectKey() == null || item.coverObjectKey().isBlank() ? null : storageService.presignedReadUrl(item.coverObjectKey()),
                        item.durationSeconds(), item.viewCount(), item.likeCount(), item.commentCount(), item.categoryId()))
                .toList();
        long total = videoMapper.countPublishedByAuthor(userId);

        Map<String, Object> user = new HashMap<>();
        user.put("id", profile.id());
        user.put("nickname", profile.nickname());
        user.put("avatar", profile.avatar() == null || profile.avatar().isBlank() ? null : storageService.presignedReadUrl(profile.avatar()));
        user.put("bio", profile.bio());
        user.put("followerCount", followMapper.countFollowers(userId));
        user.put("followingCount", followMapper.countFollowing(userId));
        user.put("likeCount", stats == null ? 0L : ((Number) stats.getOrDefault("likeCount", 0L)).longValue());
        user.put("videoCount", total);
        boolean self = viewerId != null && viewerId == userId;
        user.put("isSelf", self);
        user.put("following", !self && viewerId != null && followMapper.hasFollowed(viewerId, userId) > 0);

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("videos", videos);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("hasMore", (long) page * pageSize < total);
        return result;
    }
}
