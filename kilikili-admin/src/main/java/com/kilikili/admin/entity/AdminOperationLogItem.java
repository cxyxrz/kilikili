package com.kilikili.admin.entity;

import java.time.LocalDateTime;

public record AdminOperationLogItem(
        Long id,
        Long operatorId,
        String operatorName,
        String module,
        String action,
        String description,
        String ipAddress,
        String status,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt
) {
}
