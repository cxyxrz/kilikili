package com.kilikili.admin.entity;

import java.time.LocalDateTime;

public record AdminDanmakuItem(
        Long id,
        String content,
        String nickname,
        String videoTitle,
        Integer timeMs,
        LocalDateTime createdAt) {
}
