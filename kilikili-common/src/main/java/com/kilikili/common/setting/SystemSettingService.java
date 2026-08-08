package com.kilikili.common.setting;

import com.kilikili.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemSettingService {
    public static final String ALLOW_REGISTRATION = "allow_registration";
    public static final String DANMAKU_ENABLED = "danmaku_enabled";
    public static final String COMMENT_ENABLED = "comment_enabled";
    public static final String VIDEO_REVIEW_ENABLED = "video_review_enabled";
    public static final String LIKE_ENABLED = "like_enabled";
    public static final String COIN_ENABLED = "coin_enabled";
    public static final String FAVORITE_ENABLED = "favorite_enabled";
    public static final String FOLLOW_ENABLED = "follow_enabled";

    private final SystemSettingMapper systemSettingMapper;

    public SystemSettingService(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
    }

    public boolean isEnabled(String key) {
        String value = systemSettingMapper.get(key);
        return value == null || Boolean.parseBoolean(value);
    }

    public void requireEnabled(String key, String message) {
        if (!isEnabled(key)) throw new BusinessException(message);
    }

    public SystemSettings current() {
        Map<String, String> values = systemSettingMapper.list().stream()
                .collect(Collectors.toMap(SystemSettingEntry::key, SystemSettingEntry::value));
        return new SystemSettings(
                enabled(values, ALLOW_REGISTRATION),
                enabled(values, DANMAKU_ENABLED),
                enabled(values, COMMENT_ENABLED),
                enabled(values, VIDEO_REVIEW_ENABLED),
                enabled(values, LIKE_ENABLED),
                enabled(values, COIN_ENABLED),
                enabled(values, FAVORITE_ENABLED),
                enabled(values, FOLLOW_ENABLED)
        );
    }

    @Transactional
    public SystemSettings save(SystemSettings settings, Long updatedBy) {
        upsert(ALLOW_REGISTRATION, settings.allowRegistration(), "允许新用户注册", updatedBy);
        upsert(DANMAKU_ENABLED, settings.danmakuEnabled(), "弹幕功能", updatedBy);
        upsert(COMMENT_ENABLED, settings.commentEnabled(), "评论功能", updatedBy);
        upsert(VIDEO_REVIEW_ENABLED, settings.videoReviewEnabled(), "视频投稿需要审核", updatedBy);
        upsert(LIKE_ENABLED, settings.likeEnabled(), "点赞功能", updatedBy);
        upsert(COIN_ENABLED, settings.coinEnabled(), "投币功能", updatedBy);
        upsert(FAVORITE_ENABLED, settings.favoriteEnabled(), "收藏功能", updatedBy);
        upsert(FOLLOW_ENABLED, settings.followEnabled(), "关注功能", updatedBy);
        return current();
    }

    private boolean enabled(Map<String, String> values, String key) {
        return !values.containsKey(key) || Boolean.parseBoolean(values.get(key));
    }

    private void upsert(String key, boolean value, String description, Long updatedBy) {
        systemSettingMapper.upsert(key, Boolean.toString(value), description, updatedBy);
    }
}
