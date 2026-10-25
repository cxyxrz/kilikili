package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record TranscodeTask(Long id, Long videoId, String status, Integer attemptCount, Integer maxAttempts,
                            String errorMessage, String hlsPlaylistKey, String renditions,
                            LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt) {
}
