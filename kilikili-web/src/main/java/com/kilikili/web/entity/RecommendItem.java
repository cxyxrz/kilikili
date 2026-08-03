package com.kilikili.web.entity;

public record RecommendItem(Long id, String title, String authorName, String coverUrl, Integer durationSeconds,
                            Long viewCount, Long likeCount, Long commentCount, Long categoryId) { }
