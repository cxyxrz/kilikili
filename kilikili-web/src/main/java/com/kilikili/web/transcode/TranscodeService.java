package com.kilikili.web.transcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.TranscodeTask;
import com.kilikili.web.entity.TranscodeTaskInsert;
import com.kilikili.web.entity.Video;
import com.kilikili.web.mapper.TranscodeTaskMapper;
import com.kilikili.web.mapper.VideoMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * FFmpeg 异步转码流水线：上传后创建任务，后台轮询认领，
 * 从 MinIO 拉取原片转出多清晰度 HLS 切片并回传，支持失败重试。
 */
@Service
public class TranscodeService {
    private static final Logger log = LoggerFactory.getLogger(TranscodeService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String M3U8_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String TS_CONTENT_TYPE = "video/mp2t";

    private final TranscodeTaskMapper taskMapper;
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final TranscodeProperties properties;
    private final List<TranscodeProperties.Rendition> renditions;
    private final AtomicInteger active = new AtomicInteger();
    private ExecutorService executor;

    public TranscodeService(TranscodeTaskMapper taskMapper, VideoMapper videoMapper,
                            StorageService storageService, TranscodeProperties properties) {
        this.taskMapper = taskMapper;
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.properties = properties;
        List<TranscodeProperties.Rendition> configured = properties.renditions();
        this.renditions = configured == null || configured.isEmpty()
                ? List.of(new TranscodeProperties.Rendition("480p", 480, "1400k", "96k"),
                new TranscodeProperties.Rendition("720p", 720, "2800k", "128k"),
                new TranscodeProperties.Rendition("1080p", 1080, "5000k", "192k"))
                : configured;
    }

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(Math.max(1, properties.maxConcurrency()));
        // 服务重启后历史 RUNNING 任务必然已无工作线程，重置回 PENDING
        taskMapper.resetStaleRunning();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public void submit(long videoId) {
        if (!properties.enabled()) {
            log.info("转码功能已关闭，跳过任务创建: videoId={}", videoId);
            return;
        }
        TranscodeTaskInsert insert = new TranscodeTaskInsert(videoId, properties.maxAttempts());
        taskMapper.insert(insert);
        log.info("转码任务已创建: taskId={}, videoId={}", insert.getId(), videoId);
    }

    @Scheduled(fixedDelayString = "${kilikili.transcode.poll-interval-ms:10000}")
    public void pollPendingTasks() {
        if (!properties.enabled()) return;
        int slots = properties.maxConcurrency() - active.get();
        if (slots <= 0) return;
        List<Long> ids = taskMapper.findPendingIds(slots);
        for (Long id : ids) {
            executor.submit(() -> process(id));
        }
    }

    private void process(long taskId) {
        active.incrementAndGet();
        try {
            TranscodeTask snapshot = taskMapper.findById(taskId);
            if (snapshot == null || !"PENDING".equals(snapshot.status())) return;
            if (taskMapper.claim(taskId) == 0) return;
            TranscodeTask task = taskMapper.findById(taskId);
            try {
                TranscodeResult result = transcode(task);
                taskMapper.complete(taskId, result.playlistKey(), result.renditions());
                log.info("转码任务完成: taskId={}, videoId={}, playlistKey={}, renditions={}",
                        taskId, task.videoId(), result.playlistKey(), result.renditions());
            } catch (Exception exception) {
                handleFailure(task, exception);
            }
        } finally {
            active.decrementAndGet();
        }
    }

    private void handleFailure(TranscodeTask task, Exception exception) {
        String message = truncate(friendlyMessage(exception), 900);
        log.error("转码任务失败: taskId={}, videoId={}, attempt={}/{}, error={}",
                task.id(), task.videoId(), task.attemptCount(), task.maxAttempts(), message);
        if (task.attemptCount() >= task.maxAttempts()) {
            taskMapper.fail(task.id(), message);
        } else {
            taskMapper.requeue(task.id(), message);
        }
    }

    private TranscodeResult transcode(TranscodeTask task) throws Exception {
        Video video = videoMapper.findById(task.videoId());
        if (video == null) throw new IllegalStateException("视频不存在: " + task.videoId());
        Path baseDir = Path.of(properties.workDir());
        Files.createDirectories(baseDir);
        Path taskDir = Files.createTempDirectory(baseDir, "task-" + task.id() + "-");
        try {
            String objectKey = video.objectKey();
            String suffix = objectKey.contains(".") ? objectKey.substring(objectKey.lastIndexOf('.')) : ".mp4";
            Path sourceFile = taskDir.resolve("source" + suffix);
            storageService.downloadObject(objectKey, sourceFile);
            log.info("原片已下载: taskId={}, objectKey={}, size={}", task.id(), objectKey, Files.size(sourceFile));

            int[] dims = probeDimensions(taskDir, sourceFile);
            int srcWidth = dims[0] <= 0 ? 1920 : dims[0];
            int srcHeight = dims[1] <= 0 ? 1080 : dims[1];
            log.info("原片信息: taskId={}, {}x{}", task.id(), srcWidth, srcHeight);

            List<TranscodeProperties.Rendition> used = renditions.stream()
                    .filter(rendition -> rendition.height() <= srcHeight)
                    .toList();
            if (used.isEmpty()) {
                used = List.of(renditions.stream()
                        .min(Comparator.comparingInt(TranscodeProperties.Rendition::height)).orElseThrow());
            }

            Path outDir = taskDir.resolve("out");
            for (TranscodeProperties.Rendition rendition : used) {
                int height = Math.min(rendition.height(), srcHeight);
                height -= height % 2;
                transcodeRendition(taskDir, sourceFile.getFileName().toString(), outDir, rendition, height);
            }
            writeMasterPlaylist(outDir, used, srcWidth, srcHeight);

            String hlsPrefix = hlsPrefix(objectKey);
            uploadOutput(outDir, hlsPrefix);
            String names = String.join(",", used.stream().map(TranscodeProperties.Rendition::name).toList());
            return new TranscodeResult(hlsPrefix + "/master.m3u8", names);
        } finally {
            deleteRecursively(taskDir);
        }
    }

    private record TranscodeResult(String playlistKey, String renditions) {
    }

    private void transcodeRendition(Path taskDir, String sourceName, Path outDir,
                                    TranscodeProperties.Rendition rendition, int height) throws Exception {
        Path renditionDir = outDir.resolve(rendition.name());
        Files.createDirectories(renditionDir);
        int videoBitrate = bitrateKbps(rendition.videoBitrate());
        List<String> args = new ArrayList<>(List.of(
                "-y", "-nostats", "-loglevel", "error",
                "-i", containerPath(sourceName),
                "-vf", "scale=-2:" + height,
                "-c:v", "libx264", "-preset", "veryfast",
                "-b:v", rendition.videoBitrate(),
                "-maxrate", Math.round(videoBitrate * 1.07) + "k",
                "-bufsize", Math.round(videoBitrate * 1.5) + "k",
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", rendition.audioBitrate(), "-ac", "2",
                "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", containerPath("out/" + rendition.name() + "/seg_%05d.ts"),
                containerPath("out/" + rendition.name() + "/index.m3u8")));
        runCommand(taskDir, "ffmpeg", args);
        log.info("清晰度转码完成: rendition={}, height={}", rendition.name(), height);
    }

    private int[] probeDimensions(Path taskDir, Path sourceFile) throws Exception {
        List<String> args = List.of(
                "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "json", containerPath(sourceFile.getFileName().toString()));
        String output = runCommand(taskDir, "ffprobe", args);
        JsonNode stream = JSON.readTree(output).path("streams").path(0);
        return new int[]{stream.path("width").asInt(0), stream.path("height").asInt(0)};
    }

    private void writeMasterPlaylist(Path outDir, List<TranscodeProperties.Rendition> used,
                                     int srcWidth, int srcHeight) throws Exception {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (TranscodeProperties.Rendition rendition : used) {
            int height = Math.min(rendition.height(), srcHeight);
            height -= height % 2;
            int width = even(srcWidth * height / Math.max(1, srcHeight));
            long bandwidth = (long) (bitrateKbps(rendition.videoBitrate()) + bitrateKbps(rendition.audioBitrate())) * 1000;
            playlist.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
                    .append(",RESOLUTION=").append(width).append("x").append(height).append("\n")
                    .append(rendition.name()).append("/index.m3u8\n");
        }
        Files.writeString(outDir.resolve("master.m3u8"), playlist.toString(), StandardCharsets.UTF_8);
    }

    private void uploadOutput(Path outDir, String hlsPrefix) throws Exception {
        try (Stream<Path> paths = Files.walk(outDir)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String relative = outDir.relativize(file).toString().replace('\\', '/');
                String contentType = relative.endsWith(".m3u8") ? M3U8_CONTENT_TYPE : TS_CONTENT_TYPE;
                storageService.uploadLocalFile(hlsPrefix + "/" + relative, file, contentType);
            }
        }
        log.info("HLS 切片已回传 MinIO: prefix={}", hlsPrefix);
    }

    private String runCommand(Path taskDir, String binary, List<String> args) throws Exception {
        List<String> command = new ArrayList<>();
        if (properties.useDocker()) {
            command.add("docker");
            command.addAll(List.of("run", "--rm", "-v", taskDir.toAbsolutePath() + ":/work",
                    "--entrypoint", binary, properties.dockerImage()));
        } else {
            command.add("ffmpeg".equals(binary) ? properties.ffmpegPath() : properties.ffprobePath());
        }
        command.addAll(args);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(2, TimeUnit.HOURS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("命令执行超时: " + binary);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(binary + " 退出码 " + process.exitValue() + ": " + truncate(output, 500));
        }
        return output;
    }

    private String friendlyMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        if (properties.useDocker() && message.contains("Cannot run program \"docker\"")) {
            return "无法启动 docker 命令，请确认本机已安装 Docker 并正在运行";
        }
        return message;
    }

    private String containerPath(String relative) {
        return "/work/" + relative;
    }

    private String hlsPrefix(String objectKey) {
        String base = objectKey.contains(".")
                ? objectKey.substring(0, objectKey.lastIndexOf('.')) : objectKey;
        return base + "/hls";
    }

    private int bitrateKbps(String bitrate) {
        try {
            return Integer.parseInt(bitrate.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 1000;
        }
    }

    private int even(int value) {
        return value % 2 == 0 ? value : value + 1;
    }

    private String truncate(String value, int maxLength) {
        return value == null ? "" : value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
