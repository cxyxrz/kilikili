package com.kilikili.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DanmakuRequest(
        @NotBlank @Size(max = 100) String content,
        @NotBlank @Pattern(regexp = "SCROLL|TOP|BOTTOM") String mode,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color,
        @Min(0) @Max(86400000) Integer timeMs) { }
