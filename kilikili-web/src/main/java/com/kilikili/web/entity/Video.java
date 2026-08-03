package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record Video(Long id, Long authorId, String title, String description, String objectKey, String coverUrl,
                    String coverObjectKey, Long categoryId, Integer durationSeconds, String creationDeclaration,
                    String status, Long viewCount, Long likeCount, Long commentCount, LocalDateTime createdAt,
                    String authorName) { }