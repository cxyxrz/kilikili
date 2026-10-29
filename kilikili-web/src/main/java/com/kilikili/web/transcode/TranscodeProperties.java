package com.kilikili.web.transcode;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * FFmpeg 转码配置。executor=docker 时通过 docker run 调用镜像（默认），
 * executor=direct 时直接调用宿主机上的 ffmpeg/ffprobe 二进制。
 */
@ConfigurationProperties(prefix = "kilikili.transcode")
public record TranscodeProperties(
        boolean enabled,
        String executor,
        String dockerImage,
        String ffmpegPath,
        String ffprobePath,
        String workDir,
        long pollIntervalMs,
        int maxAttempts,
        int maxConcurrency,
        int segmentSeconds,
        long retryBackoffMs,
        long retryBackoffMaxMs,
        String videoPreset,
        List<Rendition> renditions) {

    public record Rendition(String name, int height, String videoBitrate, String audioBitrate) {
    }

    public boolean useDocker() {
        return "docker".equalsIgnoreCase(executor);
    }

    /** HLS 单片段时长，默认 6 秒；片段边界强制对齐关键帧，保证切片可独立解码 */
    public int segmentSeconds() {
        return segmentSeconds <= 0 ? 6 : Math.min(segmentSeconds, 10);
    }

    public long pollIntervalMs() {
        return pollIntervalMs <= 0 ? 10_000 : pollIntervalMs;
    }

    /** 重试指数退避的基准等待时间 */
    public long retryBackoffMs() {
        return retryBackoffMs <= 0 ? 5_000 : retryBackoffMs;
    }

    public long retryBackoffMaxMs() {
        return retryBackoffMaxMs <= 0 ? 60_000 : retryBackoffMaxMs;
    }

    public int maxAttempts() {
        return maxAttempts <= 0 ? 3 : maxAttempts;
    }

    public int maxConcurrency() {
        return maxConcurrency <= 0 ? 2 : maxConcurrency;
    }

    public String videoPreset() {
        return videoPreset == null || videoPreset.isBlank() ? "veryfast" : videoPreset;
    }
}
