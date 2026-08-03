package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record FollowerItem(Long id, String nickname, String avatar, LocalDateTime followedAt) { }
