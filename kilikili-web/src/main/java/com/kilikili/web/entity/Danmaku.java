package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record Danmaku(Long id, Long videoId, Long userId, String content, String mode, String color, Integer timeMs,
                      LocalDateTime createdAt) { }
