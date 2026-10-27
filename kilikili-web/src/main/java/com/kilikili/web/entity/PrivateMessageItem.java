package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record PrivateMessageItem(Long id, Long fromId, Long toId, String content, LocalDateTime readAt,
                                 LocalDateTime createdAt) { }
