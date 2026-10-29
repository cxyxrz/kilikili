package com.kilikili.web.entity;

import java.time.LocalDateTime;

/**
 * 直播间：status 取值 READY(待开播) / LIVING(直播中) / ENDED(已结束)。
 */
public record LiveRoom(Long id, Long userId, String title, String coverObjectKey, String streamKey, String status,
                       Integer viewerCount, Integer danmakuCount, LocalDateTime startedAt, LocalDateTime endedAt,
                       LocalDateTime createdAt, String authorName, String authorAvatar) { }
