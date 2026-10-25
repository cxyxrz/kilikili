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
        List<Rendition> renditions) {

    public record Rendition(String name, int height, String videoBitrate, String audioBitrate) {
    }

    public boolean useDocker() {
        return "docker".equalsIgnoreCase(executor);
    }
}
