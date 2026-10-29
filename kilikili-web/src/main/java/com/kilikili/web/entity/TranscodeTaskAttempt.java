package com.kilikili.web.entity;

import java.time.LocalDateTime;

/** 单次转码尝试记录，用于统计重试次数、成功率与耗时 */
public record TranscodeTaskAttempt(Long id, Long taskId, Long videoId, Integer attemptNo, String status,
                                   String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt,
                                   Long durationMs) {
}
