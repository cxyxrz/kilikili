package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record Account(Long id, String username, String passwordHash, String nickname, String avatar, LocalDateTime createdAt, String status) { }
