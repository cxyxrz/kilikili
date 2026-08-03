package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record NotificationItem(Long id, String type, String content, Long videoId, LocalDateTime readAt,
                               LocalDateTime createdAt, String actorName, String actorAvatar) { }
