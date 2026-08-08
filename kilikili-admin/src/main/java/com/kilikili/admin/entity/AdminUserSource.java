package com.kilikili.admin.entity;

import java.time.LocalDateTime;

public record AdminUserSource(
        Long id,
        String username,
        String email,
        String nickname,
        String avatar,
        String gender,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
}
