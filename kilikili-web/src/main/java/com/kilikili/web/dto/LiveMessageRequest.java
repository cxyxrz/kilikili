package com.kilikili.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveMessageRequest(@NotBlank @Size(max = 100) String content) { }
