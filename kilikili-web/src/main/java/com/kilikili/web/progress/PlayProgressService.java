package com.kilikili.web.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 播放进度合并写。
 * <p>
 * 上报时只写 Redis（最新进度）并把延迟任务顺延到 delayMs 之后；定时任务每 flushIntervalMs 扫描一次到期任务，
 * 用 Redis 中的最新进度与「上次落库值」比较：一致说明该进度已落库，无需处理；不一致说明有新进度，写入 MySQL。
 */
@Service
public class PlayProgressService {
    private static final Logger log = LoggerFactory.getLogger(PlayProgressService.class);
    private static final String LATEST_PREFIX = "video:progress:";
    private static final String FLUSHED_PREFIX = "video:progress:flushed:";
    private static final String DELAY_KEY = "video:progress:delay";

    private final StringRedisTemplate redisTemplate;
    private final PlayProgressMapper playProgressMapper;
    private final PlayProgressProperties properties;
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    public PlayProgressService(StringRedisTemplate redisTemplate, PlayProgressMapper playProgressMapper, PlayProgressProperties properties) {
        this.redisTemplate = redisTemplate;
        this.playProgressMapper = playProgressMapper;
        this.properties = properties;
    }

    /**
     * 上报播放进度：只写 Redis + 登记延迟任务，不直接写库。
     */
    public void report(long userId, long videoId, int progressSeconds, int durationSeconds) {
        if (!properties.enabled()) return;
        int duration = Math.max(0, durationSeconds);
        int progress = Math.max(0, progressSeconds);
        if (duration > 0) progress = Math.min(progress, duration);
        String member = userId + ":" + videoId;
        Duration ttl = Duration.ofSeconds(properties.cacheTtlSeconds());
        redisTemplate.opsForValue().set(LATEST_PREFIX + member, encode(progress, duration), ttl);
        // 持续上报会不断顺延任务，停止上报约 delayMs 后任务才到期
        redisTemplate.opsForZSet().add(DELAY_KEY, member, System.currentTimeMillis() + properties.delayMs());
    }

    /**
     * 读取播放进度：优先取 Redis 中尚未落库的最新值，没有则回退数据库。
     */
    public PlayProgressSnapshot current(long userId, long videoId) {
        if (!properties.enabled()) return null;
        String cached = redisTemplate.opsForValue().get(LATEST_PREFIX + userId + ":" + videoId);
        if (cached != null) {
            String[] parts = cached.split(":");
            int progress = Integer.parseInt(parts[0]);
            int duration = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return new PlayProgressSnapshot(progress, duration);
        }
        return playProgressMapper.find(userId, videoId);
    }

    @Scheduled(initialDelayString = "${kilikili.progress.initial-delay-ms:5000}",
            fixedDelayString = "${kilikili.progress.flush-interval-ms:20000}")
    public void flushDue() {
        if (!properties.enabled()) return;
        if (!flushing.compareAndSet(false, true)) return;
        try {
            long now = System.currentTimeMillis();
            Set<String> members = redisTemplate.opsForZSet().rangeByScore(DELAY_KEY, 0, now, 0, properties.batchSize());
            if (members == null) return;
            for (String member : members) {
                // 先移除再处理，避免并发重复落库
                Long removed = redisTemplate.opsForZSet().remove(DELAY_KEY, member);
                if (removed == null || removed == 0) continue;
                flushOne(member);
            }
        } catch (Exception e) {
            log.error("播放进度合并写任务异常", e);
        } finally {
            flushing.set(false);
        }
    }

    private void flushOne(String member) {
        String latest = redisTemplate.opsForValue().get(LATEST_PREFIX + member);
        if (latest == null) return;
        String flushed = redisTemplate.opsForValue().get(FLUSHED_PREFIX + member);
        // 与上次落库值一致：期间没有产生新进度，直接跳过
        if (latest.equals(flushed)) return;
        int separator = member.indexOf(':');
        long userId = Long.parseLong(member.substring(0, separator));
        long videoId = Long.parseLong(member.substring(separator + 1));
        String[] parts = latest.split(":");
        int progress = Integer.parseInt(parts[0]);
        int duration = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        playProgressMapper.upsert(userId, videoId, progress, duration);
        redisTemplate.opsForValue().set(FLUSHED_PREFIX + member, latest, Duration.ofSeconds(properties.cacheTtlSeconds()));
        log.info("播放进度已落库: userId={}, videoId={}, progress={}s, duration={}s", userId, videoId, progress, duration);
    }

    private String encode(int progress, int duration) {
        return progress + ":" + duration;
    }
}
