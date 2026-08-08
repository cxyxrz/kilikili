package com.kilikili.admin.entity;

import java.time.LocalDateTime;

public record AdminUserItem(
        Long id,
        String username,
        String email,
        String nickname,
        String avatarUrl,
        String gender,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
}
