package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record HistoryVideoItem(Long id, String title, String authorName, String coverUrl, Integer durationSeconds,
                               Long viewCount, Long likeCount, Long commentCount, LocalDateTime watchedAt) { }
