package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record LiveMessage(Long id, Long roomId, Long userId, String type, String content, LocalDateTime createdAt,
                          String nickname, String avatar) { }
