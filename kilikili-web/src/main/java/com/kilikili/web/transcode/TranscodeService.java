package com.kilikili.web.transcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.TranscodeTask;
import com.kilikili.web.entity.TranscodeTaskAttemptInsert;
import com.kilikili.web.entity.TranscodeTaskInsert;
import com.kilikili.web.entity.Video;
import com.kilikili.web.mapper.TranscodeAttemptMapper;
import com.kilikili.web.mapper.TranscodeProgressMapper;
import com.kilikili.web.mapper.TranscodeTaskMapper;
import com.kilikili.web.mapper.VideoMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * FFmpeg 异步转码流水线。
 *
 * <p>队列：DB 为唯一任务来源，进程内 {@link TranscodeQueue} 负责即时唤醒与去重，
 * 定时扫描作为重启/队列满时的兜底；工作线程数由 maxConcurrency 控制形成背压。</p>
 * <p>重试：失败后按指数退避（5s、10s、20s...）重新入队，超过 maxAttempts 才标记 FAILED，
 * 每次尝试写入 transcode_task_attempt 便于统计成功率。</p>
 * <p>进度：解析 ffmpeg -progress 的 out_time/speed/fps，按阶段折算为 0~100 落库，
 * 前端轮询 /api/videos/{id}/transcode/progress 即可回显。</p>
 * <p>切片：单次解码 + split 滤镜并行转多档，var_stream_map 一次输出多码率 HLS，
 * 相比逐档各跑一遍 ffmpeg（每档重复解码）显著缩短总耗时；片段边界按关键帧对齐。</p>
 */
@Service
public class TranscodeService {
    private static final Logger log = LoggerFactory.getLogger(TranscodeService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String M3U8_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String TS_CONTENT_TYPE = "video/mp2t";
    /** 进度区间：下载 2-8、分析 9、转码 10-95、回传 96-99、完成 100 */
    private static final int TRANSCODE_START = 10;
    private static final int TRANSCODE_END = 95;

    private final TranscodeTaskMapper taskMapper;
    private final TranscodeProgressMapper progressMapper;
    private final TranscodeAttemptMapper attemptMapper;
    private final VideoMapper videoMapper;
    private final StorageService storageService;
    private final TranscodeProperties properties;
    private final TranscodeQueue queue;
    private final List<TranscodeProperties.Rendition> renditions;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger threadSeq = new AtomicInteger();
    /** 已安排退避重试的任务，兜底扫描需跳过它们，否则会绕过退避立即重试 */
    private final java.util.Set<Long> scheduledRetries = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ExecutorService workers;
    private ScheduledExecutorService retryScheduler;

    public TranscodeService(TranscodeTaskMapper taskMapper, TranscodeProgressMapper progressMapper,
                            TranscodeAttemptMapper attemptMapper, VideoMapper videoMapper,
                            StorageService storageService, TranscodeProperties properties, TranscodeQueue queue) {
        this.taskMapper = taskMapper;
        this.progressMapper = progressMapper;
        this.attemptMapper = attemptMapper;
        this.videoMapper = videoMapper;
        this.storageService = storageService;
        this.properties = properties;
        this.queue = queue;
        List<TranscodeProperties.Rendition> configured = properties.renditions();
        this.renditions = configured == null || configured.isEmpty()
                ? List.of(new TranscodeProperties.Rendition("480p", 480, "1400k", "96k"),
                new TranscodeProperties.Rendition("720p", 720, "2800k", "128k"),
                new TranscodeProperties.Rendition("1080p", 1080, "5000k", "192k"))
                : configured;
    }

    @PostConstruct
    public void init() {
        // 服务重启后历史 RUNNING 任务必然已无工作线程，重置回 PENDING；未完成的尝试记为中断
        taskMapper.resetStaleRunning();
        attemptMapper.failStaleRunning();
        retryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "transcode-retry");
            thread.setDaemon(true);
            return thread;
        });
        workers = Executors.newFixedThreadPool(properties.maxConcurrency(), runnable -> {
            Thread thread = new Thread(runnable, "transcode-worker-" + threadSeq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        for (int i = 0; i < properties.maxConcurrency(); i++) {
            workers.submit(this::workerLoop);
        }
        log.info("转码工作线程已启动: concurrency={}", properties.maxConcurrency());
    }

    @PreDestroy
    public void shutdown() {
        workers.shutdownNow();
        retryScheduler.shutdownNow();
    }

    /** 工作线程：优先消费进程内队列，队列空闲时由定时扫描补充 */
    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Long taskId = queue.take(1000);
                if (taskId != null) process(taskId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.error("转码工作线程异常: {}", exception.getMessage(), exception);
            }
        }
    }

    public void submit(long videoId) {
        if (!properties.enabled()) {
            log.info("转码功能已关闭，跳过任务创建: videoId={}", videoId);
            return;
        }
        TranscodeTaskInsert insert = new TranscodeTaskInsert(videoId, properties.maxAttempts());
        taskMapper.insert(insert);
        progressMapper.upsert(insert.getId(), videoId, 0, "QUEUED", null, null, null, "等待转码");
        queue.offer(insert.getId(), false);
        log.info("转码任务已入队: taskId={}, videoId={}, queueSize={}", insert.getId(), videoId, queue.size());
    }

    public int queueSize() {
        return queue.size();
    }

    public int activeCount() {
        return active.get();
    }

    /** 兜底扫描：处理重启前遗留、队列已满未被进程内队列接收的任务 */
    @Scheduled(fixedDelayString = "${kilikili.transcode.poll-interval-ms:10000}")
    public void pollPendingTasks() {
        if (!properties.enabled()) return;
        int slots = properties.maxConcurrency() - active.get() - queue.size();
        if (slots <= 0) return;
        List<Long> ids = taskMapper.findPendingIds(slots);
        for (Long id : ids) {
            if (scheduledRetries.contains(id)) continue;
            queue.offer(id, false);
        }
        if (!ids.isEmpty()) log.debug("兜底扫描补充 {} 个待转码任务", ids.size());
    }

    private void process(long taskId) {
        active.incrementAndGet();
        long attemptId = 0L;
        TranscodeTask task = null;
        try {
            TranscodeTask snapshot = taskMapper.findById(taskId);
            if (snapshot == null || !"PENDING".equals(snapshot.status())) return;
            if (taskMapper.claim(taskId) == 0) return;
            task = taskMapper.findById(taskId);
            int attemptNo = task.attemptCount() == null ? 1 : task.attemptCount();
            attemptId = startAttempt(task, attemptNo);

            TranscodeResult result = transcode(task, new ProgressReporter(task, attemptNo));
            taskMapper.complete(taskId, result.playlistKey(), result.renditions());
            attemptMapper.finish(attemptId, "COMPLETED", null);
            progressMapper.upsert(taskId, task.videoId(), 100, "COMPLETED", null, null, null,
                    "转码完成，已生成 " + result.renditions());
            log.info("转码任务完成: taskId={}, videoId={}, rendition={}, playlistKey={}",
                    taskId, task.videoId(), result.renditions(), result.playlistKey());
        } catch (Exception exception) {
            if (task != null) handleFailure(task, attemptId, exception);
            else log.error("转码任务预处理失败: taskId={}, error={}", taskId, exception.getMessage());
        } finally {
            active.decrementAndGet();
        }
    }

    private long startAttempt(TranscodeTask task, int attemptNo) {
        TranscodeTaskAttemptInsert insert = new TranscodeTaskAttemptInsert(task.id(), task.videoId(), attemptNo, "RUNNING");
        attemptMapper.insert(insert);
        progressMapper.upsert(task.id(), task.videoId(), 1, "RUNNING", null, null, null,
                attemptNo > 1 ? "第 " + attemptNo + " 次尝试" : "开始转码");
        return insert.getId() == null ? 0L : insert.getId();
    }

    /**
     * 失败处理：指数退避后重新入队；达到上限标记 FAILED 并保留错误信息。
     * 退避通过独立的调度线程延迟入队，避免立即重试把失败任务反复打满工作线程。
     */
    private void handleFailure(TranscodeTask task, long attemptId, Exception exception) {
        String message = truncate(friendlyMessage(exception), 900);
        log.error("转码任务失败: taskId={}, videoId={}, attempt={}/{}, error={}",
                task.id(), task.videoId(), task.attemptCount(), task.maxAttempts(), message);
        if (attemptId > 0) attemptMapper.finish(attemptId, "FAILED", message);
        if (task.attemptCount() != null && task.attemptCount() >= task.maxAttempts()) {
            taskMapper.fail(task.id(), message);
            progressMapper.upsert(task.id(), task.videoId(), 0, "FAILED", null, null, null, message);
            return;
        }
        taskMapper.requeue(task.id(), message);
        long delay = backoffMillis(task.attemptCount() == null ? 1 : task.attemptCount());
        int delaySeconds = (int) Math.ceil(delay / 1000.0);
        progressMapper.upsert(task.id(), task.videoId(), 0, "RETRYING", null, null, delaySeconds,
                "失败，将在 " + delaySeconds + " 秒后第 "
                        + (Math.min(task.attemptCount() == null ? 2 : task.attemptCount() + 1, task.maxAttempts()))
                        + " 次重试：" + truncate(message, 200));
        try {
            scheduledRetries.add(task.id());
            retryScheduler.schedule(() -> {
                scheduledRetries.remove(task.id());
                queue.offer(task.id(), true);
            }, delay, TimeUnit.MILLISECONDS);
            log.info("转码任务已安排重试: taskId={}, delayMs={}", task.id(), delay);
        } catch (Exception exception2) {
            log.warn("重试调度失败，退回定时扫描: taskId={}, error={}", task.id(), exception2.getMessage());
        }
    }

    private long backoffMillis(int attemptCount) {
        long base = properties.retryBackoffMs();
        long delay = base * (1L << Math.max(0, Math.min(attemptCount - 1, 5)));
        return Math.min(delay, properties.retryBackoffMaxMs());
    }

    private TranscodeResult transcode(TranscodeTask task, ProgressReporter reporter) throws Exception {
        Video video = videoMapper.findById(task.videoId());
        if (video == null) throw new IllegalStateException("视频不存在: " + task.videoId());
        Path baseDir = Path.of(properties.workDir());
        Files.createDirectories(baseDir);
        Path taskDir = Files.createTempDirectory(baseDir, "task-" + task.id() + "-");
        try {
            String objectKey = video.objectKey();
            String suffix = objectKey.contains(".") ? objectKey.substring(objectKey.lastIndexOf('.')) : ".mp4";
            Path sourceFile = taskDir.resolve("source" + suffix);
            reporter.stage(2, "DOWNLOAD", "下载原片");
            storageService.downloadObject(objectKey, sourceFile);
            log.info("原片已下载: taskId={}, objectKey={}, size={}", task.id(), objectKey, Files.size(sourceFile));
            reporter.stage(8, "PROBE", "解析视频信息");

            ProbeResult probe = probe(taskDir, sourceFile);
            log.info("原片信息: taskId={}, {}x{}, duration={}s, audio={}", task.id(), probe.width(), probe.height(),
                    String.format("%.1f", probe.durationSeconds()), probe.hasAudio());

            List<TranscodeProperties.Rendition> used = renditions.stream()
                    .filter(rendition -> rendition.height() <= probe.height())
                    .toList();
            if (used.isEmpty()) {
                used = List.of(renditions.stream()
                        .min(Comparator.comparingInt(TranscodeProperties.Rendition::height)).orElseThrow());
            }

            Path outDir = taskDir.resolve("out");
            Files.createDirectories(outDir);
            reporter.stage(TRANSCODE_START, "TRANSCODE", "转码 " + used.size() + " 个码率");
            transcodeAll(taskDir, sourceFile.getFileName().toString(), outDir, used, probe, reporter);
            writeMasterPlaylist(outDir, used, probe.width(), probe.height());

            reporter.stage(96, "UPLOAD", "回传 HLS 切片");
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

    private record ProbeResult(int width, int height, double durationSeconds, double fps, boolean hasAudio) {
    }

    /**
     * 单次解码 + split 多路缩放，再通过 var_stream_map 一次产出多档 HLS。
     * 只解码一次原片，是相对「每档各跑一遍 ffmpeg」的主要耗时优化点。
     */
    private void transcodeAll(Path taskDir, String sourceName, Path outDir,
                              List<TranscodeProperties.Rendition> used, ProbeResult probe,
                              ProgressReporter reporter) throws Exception {
        int count = used.size();
        int segment = properties.segmentSeconds();
        if (count == 1) {
            transcodeSingle(taskDir, sourceName, outDir, used.get(0),
                    Math.min(used.get(0).height(), probe.height()), segment, probe, reporter);
            return;
        }

        List<String> args = new ArrayList<>(List.of(
                "-y", "-nostats", "-loglevel", "error", "-progress", "pipe:1",
                "-i", containerPath(sourceName)));
        StringBuilder filter = new StringBuilder("split=").append(count);
        for (int i = 0; i < count; i++) filter.append("[v").append(i).append("]");
        filter.append(';');
        for (int i = 0; i < count; i++) {
            if (i > 0) filter.append(';');
            int height = even(Math.min(used.get(i).height(), probe.height()));
            filter.append("[v").append(i).append("]scale=-2:").append(height).append("[vout").append(i).append(']');
        }
        args.add("-filter_complex");
        args.add(filter.toString());

        StringBuilder streamMap = new StringBuilder();
        for (int i = 0; i < count; i++) {
            args.add("-map");
            args.add("[vout" + i + "]");
            if (probe.hasAudio()) {
                args.add("-map");
                args.add("0:a:0");
            }
            if (i > 0) streamMap.append(' ');
            streamMap.append("v:").append(i);
            if (probe.hasAudio()) streamMap.append(",a:").append(i);
        }

        for (int i = 0; i < count; i++) {
            TranscodeProperties.Rendition rendition = used.get(i);
            int gop = gop(probe.fps(), segment);
            int kbps = bitrateKbps(rendition.videoBitrate());
            args.addAll(List.of(
                    "-c:v:" + i, "libx264", "-preset", properties.videoPreset(),
                    "-profile:v:" + i, "main",
                    "-b:v:" + i, rendition.videoBitrate(),
                    "-maxrate:v:" + i, Math.round(kbps * 1.07) + "k",
                    "-bufsize:v:" + i, Math.round(kbps * 1.5) + "k",
                    "-g:v:" + i, String.valueOf(gop),
                    "-keyint_min:v:" + i, String.valueOf(gop),
                    "-sc_threshold:v:" + i, "0"));
            if (probe.hasAudio()) {
                args.addAll(List.of("-c:a:" + i, "aac",
                        "-b:a:" + i, rendition.audioBitrate(), "-ac:a:" + i, "2"));
            }
            // var_stream_map 用 %v 输出，目录按序号创建，转完后重命名成 480p/720p 等清晰度名
            Files.createDirectories(outDir.resolve(String.valueOf(i)));
        }
        args.addAll(List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(segment),
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-force_key_frames", "expr:gte(t,n_forced*" + segment + ")",
                "-var_stream_map", streamMap.toString(),
                "-hls_segment_filename", containerPath("out/%v/seg_%05d.ts"),
                containerPath("out/%v/index.m3u8")));

        runFfmpeg(taskDir, args, probe.durationSeconds(), reporter);
        for (int i = 0; i < count; i++) {
            Path from = outDir.resolve(String.valueOf(i));
            Path to = outDir.resolve(used.get(i).name());
            Files.move(from, to);
        }
        log.info("多码率单遍转码完成: renditions={}", used.stream().map(TranscodeProperties.Rendition::name).toList());
    }

    /** 只有一路清晰度时省去 split/var_stream_map，直接 scale 输出 */
    private void transcodeSingle(Path taskDir, String sourceName, Path outDir,
                                 TranscodeProperties.Rendition rendition, int height, int segment,
                                 ProbeResult probe, ProgressReporter reporter) throws Exception {
        Path renditionDir = outDir.resolve(rendition.name());
        Files.createDirectories(renditionDir);
        int kbps = bitrateKbps(rendition.videoBitrate());
        int gop = gop(probe.fps(), segment);
        List<String> args = new ArrayList<>(List.of(
                "-y", "-nostats", "-loglevel", "error", "-progress", "pipe:1",
                "-i", containerPath(sourceName),
                "-vf", "scale=-2:" + even(height),
                "-c:v", "libx264", "-preset", properties.videoPreset(),
                "-b:v", rendition.videoBitrate(),
                "-maxrate", Math.round(kbps * 1.07) + "k",
                "-bufsize", Math.round(kbps * 1.5) + "k",
                "-g", String.valueOf(gop), "-keyint_min", String.valueOf(gop), "-sc_threshold", "0"));
        if (probe.hasAudio()) {
            args.addAll(List.of("-c:a", "aac", "-b:a", rendition.audioBitrate(), "-ac", "2"));
        }
        args.addAll(List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(segment),
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-force_key_frames", "expr:gte(t,n_forced*" + segment + ")",
                "-hls_segment_filename", containerPath("out/" + rendition.name() + "/seg_%05d.ts"),
                containerPath("out/" + rendition.name() + "/index.m3u8")));
        runFfmpeg(taskDir, args, probe.durationSeconds(), reporter);
        log.info("清晰度转码完成: rendition={}, height={}", rendition.name(), height);
    }

    private ProbeResult probe(Path taskDir, Path sourceFile) throws Exception {
        List<String> args = List.of(
                "-v", "error",
                "-show_entries", "stream=codec_type,width,height,avg_frame_rate:format=duration",
                "-of", "json", containerPath(sourceFile.getFileName().toString()));
        String output = runCommand(taskDir, "ffprobe", args);
        JsonNode root = JSON.readTree(output);
        JsonNode videoStream = null;
        boolean hasAudio = false;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText()) && videoStream == null) videoStream = stream;
            if ("audio".equals(stream.path("codec_type").asText())) hasAudio = true;
        }
        int width = videoStream == null ? 1920 : videoStream.path("width").asInt(1920);
        int height = videoStream == null ? 1080 : videoStream.path("height").asInt(1080);
        double fps = parseFrameRate(videoStream == null ? "" : videoStream.path("avg_frame_rate").asText());
        double duration = root.path("format").path("duration").asDouble(0);
        return new ProbeResult(width, height, duration, fps, hasAudio);
    }

    private double parseFrameRate(String value) {
        if (value == null || value.isBlank() || "0/0".equals(value)) return 25;
        if (!value.contains("/")) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                return 25;
            }
        }
        String[] parts = value.split("/");
        try {
            double denominator = Double.parseDouble(parts[1]);
            return denominator == 0 ? 25 : Double.parseDouble(parts[0]) / denominator;
        } catch (Exception exception) {
            return 25;
        }
    }

    private int gop(double fps, int segmentSeconds) {
        return Math.max(1, (int) Math.round(Math.max(fps, 1) * segmentSeconds));
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

    /**
     * 执行 ffmpeg 并解析 -progress 输出。stdout 是进度流，stderr 单独收集用于报错，
     * 不能合并两路否则进度行会被日志污染。
     */
    private void runFfmpeg(Path taskDir, List<String> args, double durationSeconds,
                           ProgressReporter reporter) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command(taskDir, "ffmpeg", args));
        builder.redirectErrorStream(false);
        Process process = builder.start();
        StringBuilder stderr = new StringBuilder();
        Thread errReader = new Thread(() -> readLimited(process.getErrorStream(), stderr, 8000), "transcode-stderr");
        errReader.setDaemon(true);
        errReader.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            long lastReportAt = 0L;
            int lastPercent = TRANSCODE_START;
            ProgressSample sample = new ProgressSample();
            while ((line = reader.readLine()) != null) {
                if (!sample.apply(line)) continue;
                Double outTime = sample.outTimeSeconds();
                if (outTime == null || durationSeconds <= 0) continue;
                long now = System.currentTimeMillis();
                int percent = percentOf(outTime, durationSeconds);
                // 每 1.5 秒或进度变化超过 2% 才落库，避免高频写放大
                if (now - lastReportAt < 1500 && Math.abs(percent - lastPercent) < 2) continue;
                lastReportAt = now;
                lastPercent = percent;
                Integer eta = etaSeconds(outTime, durationSeconds, sample.speed());
                reporter.progress(percent, sample.fps(), sample.speed(), eta);
            }
        }
        boolean finished = process.waitFor(2, TimeUnit.HOURS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg 执行超时");
        }
        errReader.join(2000);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("ffmpeg 退出码 " + process.exitValue() + ": " + truncate(stderr.toString(), 600));
        }
    }

    /** ffmpeg -progress 输出为 key=value 行，out_time/fps/speed 分布在多行，累积成一次采样 */
    private static final class ProgressSample {
        private Double outTimeSeconds;
        private BigDecimal fps;
        private BigDecimal speed;

        /** 返回本行是否携带了 out_time（用于触发一次进度上报） */
        private boolean apply(String line) {
            int index = line.indexOf('=');
            if (index <= 0) return false;
            String key = line.substring(0, index);
            String value = line.substring(index + 1).trim();
            switch (key) {
                case "out_time_us", "out_time_ms" -> {
                    try {
                        outTimeSeconds = Double.parseDouble(value) / 1_000_000d;
                        return true;
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                }
                case "fps" -> {
                    if (!"N/A".equals(value)) {
                        try {
                            fps = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                case "speed" -> {
                    String text = value.endsWith("x") ? value.substring(0, value.length() - 1) : value;
                    if (!"N/A".equals(text)) {
                        try {
                            speed = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                default -> {
                }
            }
            return false;
        }

        private Double outTimeSeconds() {
            return outTimeSeconds;
        }

        private BigDecimal fps() {
            return fps;
        }

        private BigDecimal speed() {
            return speed;
        }
    }

    private int percentOf(double outTimeSeconds, double durationSeconds) {
        double ratio = Math.max(0d, Math.min(1d, outTimeSeconds / durationSeconds));
        return TRANSCODE_START + (int) Math.round(ratio * (TRANSCODE_END - TRANSCODE_START));
    }

    private Integer etaSeconds(double outTimeSeconds, double durationSeconds, BigDecimal speed) {
        double remaining = Math.max(0d, durationSeconds - outTimeSeconds);
        double rate = speed == null ? 0d : speed.doubleValue();
        if (rate <= 0.01) return null;
        return (int) Math.ceil(remaining / rate);
    }

    /** 进度上报器：阶段切换强制落库，转码中的采样由调用方节流后传入 */
    private final class ProgressReporter {
        private final TranscodeTask task;
        private final int attemptNo;

        private ProgressReporter(TranscodeTask task, int attemptNo) {
            this.task = task;
            this.attemptNo = attemptNo;
        }

        void stage(int percent, String stage, String message) {
            safeUpsert(percent, stage, null, null, null, message);
        }

        void progress(int percent, BigDecimal fps, BigDecimal speed, Integer eta) {
            safeUpsert(Math.min(percent, TRANSCODE_END), "TRANSCODE", fps, speed, eta, null);
        }

        private void safeUpsert(int percent, String stage, BigDecimal fps, BigDecimal speed,
                                Integer eta, String message) {
            try {
                String text = message;
                if (text == null && attemptNo > 1) text = "第 " + attemptNo + " 次尝试";
                progressMapper.upsert(task.id(), task.videoId(), percent, stage, fps, speed, eta, text);
            } catch (Exception exception) {
                // 进度更新失败不能影响转码主流程
                log.warn("转码进度写入失败: taskId={}, error={}", task.id(), exception.getMessage());
            }
        }
    }

    private String runCommand(Path taskDir, String binary, List<String> args) throws Exception {
        Process process = new ProcessBuilder(command(taskDir, binary, args)).start();
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

    private List<String> command(Path taskDir, String binary, List<String> args) {
        List<String> command = new ArrayList<>();
        if (properties.useDocker()) {
            // scratch 镜像无 PATH，二进制在根目录，需用绝对路径作为 entrypoint
            command.add("docker");
            command.addAll(List.of("run", "--rm", "-v", taskDir.toAbsolutePath() + ":/work",
                    "--entrypoint", "/" + binary, properties.dockerImage()));
        } else {
            command.add("ffmpeg".equals(binary) ? properties.ffmpegPath() : properties.ffprobePath());
        }
        command.addAll(args);
        return command;
    }

    private void readLimited(InputStream stream, StringBuilder sink, int max) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (sink) {
                    if (sink.length() < max) sink.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
        }
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
