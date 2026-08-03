package com.kilikili.admin.entity;

public record AdminAccount(Long id, String username, String passwordHash, String nickname, String status, String role) { }
