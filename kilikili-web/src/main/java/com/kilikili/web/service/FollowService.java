package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.web.mapper.AccountMapper;
import com.kilikili.web.mapper.FollowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class FollowService {
    private final FollowMapper followMapper;
    private final AccountMapper accountMapper;
    private final NotificationService notificationService;

    public FollowService(FollowMapper followMapper, AccountMapper accountMapper, NotificationService notificationService) {
        this.followMapper = followMapper;
        this.accountMapper = accountMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public Map<String, Object> toggle(long followerId, long followingId) {
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
}
