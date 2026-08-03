package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record CreatorVideoSource(Long id, String title, String coverObjectKey, String status, Long viewCount,
                                 Long likeCount, Long commentCount, LocalDateTime createdAt) { }
