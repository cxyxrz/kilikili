package com.kilikili.web.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 转码进度快照，percent 为 0~100 的整体进度 */
public record TranscodeTaskProgress(Long taskId, Long videoId, Integer percent, String stage,
                                    BigDecimal fps, BigDecimal speed, Integer etaSeconds,
                                    String message, LocalDateTime updatedAt) {
}
