package com.kilikili.web.entity;

import java.time.LocalDateTime;

/** 弹幕防挡脸抽帧分析任务状态：PENDING/RUNNING/READY/FAILED */
public record DanmakuMaskAnalysis(Long videoId, String status, Integer frameCount, String message,
                                  LocalDateTime updatedAt) {
}
