package com.kilikili.admin.entity;

import java.time.LocalDateTime;

public record AdminCommentItem(
        Long id,
        Long parentId,
        String content,
        String username,
        String nickname,
        String videoTitle,
        Long replyCount,
        LocalDateTime createdAt
) {
}
