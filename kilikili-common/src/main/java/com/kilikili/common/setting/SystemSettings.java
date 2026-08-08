package com.kilikili.common.setting;

public record SystemSettings(
        boolean allowRegistration,
        boolean danmakuEnabled,
        boolean commentEnabled,
        boolean videoReviewEnabled,
        boolean likeEnabled,
        boolean coinEnabled,
        boolean favoriteEnabled,
        boolean followEnabled
) {
}
