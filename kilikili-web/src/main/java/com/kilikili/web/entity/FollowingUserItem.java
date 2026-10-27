package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record FollowingUserItem(Long id, String nickname, String avatar, String bio, LocalDateTime followedAt) { }
