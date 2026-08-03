package com.kilikili.web.entity;

import java.time.LocalDate;

public record AccountProfile(Long id, String username, String nickname, String avatar, String gender, LocalDate birthday, String bio) { }
