package com.kilikili.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线") String username,
                              @NotBlank @Size(min = 6, max = 72, message = "密码长度需为 6-72 位") String password,
                              @NotBlank @Size(max = 64) String nickname) { }
