package com.kilikili.web.entity;

public record RecommendSource(Long id, String title, String authorName, String coverObjectKey, Integer durationSeconds,
                              Long viewCount, Long likeCount, Long commentCount, Long categoryId) { }
