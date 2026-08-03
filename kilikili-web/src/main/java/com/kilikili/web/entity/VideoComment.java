package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record VideoComment(Long id, Long videoId, Long userId, Long parentId, String content, LocalDateTime createdAt, String nickname,
                           String avatar) { }
