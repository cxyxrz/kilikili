package com.kilikili.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProgressRequest(
        @Min(0) @Max(86400) int progressSeconds,
        @Min(0) @Max(86400) int durationSeconds) {
}
