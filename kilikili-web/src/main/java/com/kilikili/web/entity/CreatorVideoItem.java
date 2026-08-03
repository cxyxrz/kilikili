package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record CreatorVideoItem(Long id, String title, String coverUrl, String status, Long viewCount,
                               Long likeCount, Long commentCount, LocalDateTime createdAt) { }
