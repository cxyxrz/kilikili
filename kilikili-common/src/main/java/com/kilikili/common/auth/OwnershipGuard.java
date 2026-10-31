package com.kilikili.common.auth;

import com.kilikili.common.exception.BusinessException;

/**
 * 越权校验：资源操作前确认当前用户是否为资源所有者/参与者。
 * 统一在这里处理，避免各业务各自判断时漏掉空值或 Long 比较问题。
 */
public final class OwnershipGuard {
    private OwnershipGuard() {
    }

    public static void requireOwner(long currentUserId, Long ownerId, String action) {
        if (ownerId == null || ownerId != currentUserId) {
            throw new BusinessException("无权" + action);
        }
    }

    public static void requireParticipant(long currentUserId, Long ownerId, Long peerId, String action) {
        if (ownerId == null || peerId == null || (ownerId != currentUserId && peerId != currentUserId)) {
            throw new BusinessException("无权" + action);
        }
    }
}
