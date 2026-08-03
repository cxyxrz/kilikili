package com.kilikili.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusRequest(@NotBlank String status) { }
