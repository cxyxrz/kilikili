package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record HistoryVideoSource(Long id, String title, String authorName, String coverUrl, String coverObjectKey,
                                 Integer durationSeconds, Long viewCount, Long likeCount, Long commentCount,
                                 LocalDateTime watchedAt) { }
