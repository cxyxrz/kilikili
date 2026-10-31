package com.kilikili.web.cover;

import com.kilikili.common.storage.StorageService;
import com.kilikili.web.transcode.TranscodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 视频关键帧抽取：把原片从 MinIO 下载到临时目录后调用 FFmpeg 截帧，
 * 复用转码配置的 docker 镜像或宿主机二进制，按视频时长比例取 10%/35%/60% 三个位置。
 */
@Component
public class VideoFrameExtractor {
    private static final Logger log = LoggerFactory.getLogger(VideoFrameExtractor.class);
    private static final int MAX_FRAMES = 3;
    private static final int FRAME_WIDTH = 640;
    private static final long COMMAND_TIMEOUT_SECONDS = 60;

    private final StorageService storageService;
    private final TranscodeProperties properties;

    public VideoFrameExtractor(StorageService storageService, TranscodeProperties properties) {
        this.storageService = storageService;
        this.properties = properties;
    }

    public List<byte[]> extract(String objectKey, int count) {
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("视频文件不存在");
        int target = Math.max(1, Math.min(count, MAX_FRAMES));
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("kilikili-frames-");
            Path input = workDir.resolve("input" + extension(objectKey));
            storageService.downloadObject(objectKey, input);
            double duration = probeDuration(workDir, input);
            List<Double> timestamps = timestamps(duration, target);

            List<byte[]> frames = new ArrayList<>(target);
            for (int index = 0; index < timestamps.size() && frames.size() < target; index++) {
                Path output = workDir.resolve("frame-" + index + ".jpg");
                if (!grabFrame(workDir, input, timestamps.get(index), output)) continue;
                if (Files.exists(output) && Files.size(output) > 0) frames.add(Files.readAllBytes(output));
            }
            log.info("视频截帧完成: objectKey={}, duration={}s, frames={}", objectKey, String.format("%.1f", duration), frames.size());
            return frames;
        } catch (IOException exception) {
            throw new IllegalStateException("视频截帧失败：" + exception.getMessage(), exception);
        } finally {
            deleteQuietly(workDir);
        }
    }

    /** 时长为 0（解析失败或极短视频）时退回固定秒数，保证仍有画面可用 */
    private List<Double> timestamps(double duration, int count) {
        List<Double> values = new ArrayList<>(count);
        if (duration <= 0) {
            values.add(1.0);
            values.add(3.0);
            values.add(5.0);
            return values.subList(0, Math.min(count, values.size()));
        }
        double[] ratios = {0.1, 0.35, 0.6};
        for (int index = 0; index < Math.min(count, ratios.length); index++) {
            values.add(Math.max(0.3, Math.min(duration * ratios[index], Math.max(duration - 0.2, 0.3))));
        }
        return values;
    }

    private double probeDuration(Path workDir, Path input) {
        List<String> command = binaryCommand(workDir, "ffprobe");
        command.addAll(List.of("-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1"));
        command.add(containerPath(workDir, input));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return 0;
            }
            String output = new String(process.getInputStream().readAllBytes()).strip();
            return Double.parseDouble(output);
        } catch (Exception exception) {
            log.warn("解析视频时长失败，使用固定截帧时间点: {}", exception.getMessage());
            return 0;
        }
    }

    private boolean grabFrame(Path workDir, Path input, double timestamp, Path output) {
        List<String> command = binaryCommand(workDir, "ffmpeg");
        command.addAll(List.of(
                "-y", "-ss", String.format("%.2f", timestamp),
                "-i", containerPath(workDir, input),
                "-frames:v", "1",
                "-vf", "scale=" + FRAME_WIDTH + ":-2",
                "-q:v", "3"));
        command.add(containerPath(workDir, output));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception exception) {
            log.warn("截帧失败: timestamp={}s, reason={}", timestamp, exception.getMessage());
            return false;
        }
    }

    /** docker 模式下以绝对路径作为 entrypoint，普通模式直接调用宿主机二进制 */
    private List<String> binaryCommand(Path workDir, String binary) {
        List<String> command = new ArrayList<>();
        if (properties.useDocker()) {
            command.addAll(List.of("docker", "run", "--rm",
                    "-v", workDir.toAbsolutePath() + ":/work",
                    "--entrypoint", "/" + binary, properties.dockerImage()));
        } else {
            String path = "ffmpeg".equals(binary) ? properties.ffmpegPath() : properties.ffprobePath();
            command.add(path == null || path.isBlank() ? binary : path);
        }
        return command;
    }

    private String containerPath(Path workDir, Path file) {
        if (properties.useDocker()) return "/work/" + workDir.relativize(file);
        return file.toAbsolutePath().toString();
    }

    private String extension(String objectKey) {
        int dot = objectKey.lastIndexOf('.');
        return dot >= 0 && objectKey.length() - dot <= 6 ? objectKey.substring(dot) : ".mp4";
    }

    private void deleteQuietly(Path directory) {
        if (directory == null) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
