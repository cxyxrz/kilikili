package com.kilikili.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.cache.CacheGovernanceProperties;
import com.kilikili.web.cache.CacheStats;
import com.kilikili.web.cache.HotKeyDetector;
import com.kilikili.web.cache.LocalTtlCache;
import com.kilikili.web.cache.RedisMutex;
import com.kilikili.web.dto.RecommendCachePayload;
import com.kilikili.web.entity.Category;
import com.kilikili.web.entity.RecommendItem;
import com.kilikili.web.entity.RecommendSource;
import com.kilikili.web.mapper.RecommendationMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推荐页服务。缓存治理策略：
 * 1) 穿透：不存在的 categoryId 直接返回空页，空结果写短 TTL 空值缓存；
 * 2) 击穿：缓存 miss 时用 Redis 分布式锁只放一个线程回源，其余线程短暂等待后重读；
 * 3) 雪崩：TTL 加随机抖动、增加进程内 L1 缓存、Redis 故障时进入降级窗口直接回源；
 * 4) 一致性：写操作后先删一次缓存，再延迟 500ms 二次删除，覆盖事务提交前被回填的旧数据；
 * 5) 热点：滑动窗口统计访问频次，热点 key 自动延长 TTL 并写入 Redis 榜单。
 */
@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String KEY_PREFIX = "kilikili:recommend:";
    private static final String CATEGORY_KEY = KEY_PREFIX + "categories";
    /** Redis 异常后 5 秒内不再尝试访问，避免连接异常时每条请求都被拖慢 */
    private static final long REDIS_DEGRADE_MS = 5_000L;
    private static final long CATEGORY_LOCAL_TTL_MS = 60_000L;

    private final RecommendationMapper recommendationMapper;
    private final StorageService storageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.kilikili.web.search.VideoSearchService videoSearchService;
    private final CacheGovernanceProperties properties;
    private final RedisMutex redisMutex;
    private final HotKeyDetector hotKeyDetector;
    private final CacheStats cacheStats;
    /** L1 本地缓存，Redis 不可用时仍可返回最近的热数据 */
    private final LocalTtlCache<RecommendCachePayload> localCache = new LocalTtlCache<>();
    private final ScheduledExecutorService doubleDeleteExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "recommend-cache-double-delete");
        thread.setDaemon(true);
        return thread;
    });

    private volatile long redisDegradeUntil = 0L;
    private volatile Set<Long> categoryIdCache = Set.of();
    private volatile long categoryLoadedAt = 0L;

    public RecommendationService(RecommendationMapper recommendationMapper, StorageService storageService,
                                 StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                 com.kilikili.web.search.VideoSearchService videoSearchService,
                                 CacheGovernanceProperties properties, RedisMutex redisMutex,
                                 HotKeyDetector hotKeyDetector, CacheStats cacheStats) {
        this.recommendationMapper = recommendationMapper;
        this.storageService = storageService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.videoSearchService = videoSearchService;
        this.properties = properties;
        this.redisMutex = redisMutex;
        this.hotKeyDetector = hotKeyDetector;
        this.cacheStats = cacheStats;
    }

    @PreDestroy
    public void shutdown() {
        doubleDeleteExecutor.shutdown();
    }

    public List<Category> categories() { return recommendationMapper.listCategories(); }
    public List<String> suggest(String keyword) { return videoSearchService.suggest(keyword); }
    public List<Map<String, Object>> hotKeywords() { return videoSearchService.hotKeywords(); }

    /**
     * 清空推荐列表缓存：视频状态变化（审核通过、下线、自动发布）后调用。
     * 采用延迟双删：第一次删除尽快让读请求失效，延迟第二次删除覆盖「事务未提交时被旧数据回填」的窗口。
     */
    public void evictRecommendCache() {
        deleteRecommendKeys("首次删除");
        try {
            doubleDeleteExecutor.schedule(() -> deleteRecommendKeys("延迟二次删除"),
                    properties.doubleDeleteDelayMs(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            log.warn("延迟双删任务提交失败: {}", exception.getMessage());
        }
    }

    private void deleteRecommendKeys(String stage) {
        localCache.clear();
        categoryLoadedAt = 0L;
        int deleted = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build())) {
            List<String> batch = new ArrayList<>(200);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 200) {
                    deleted += deleteBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) deleted += deleteBatch(batch);
            log.info("推荐缓存{}完成: deletedKeys={}", stage, deleted);
        } catch (Exception exception) {
            log.warn("推荐缓存{}失败: {}", stage, exception.getMessage());
        }
    }

    private int deleteBatch(List<String> keys) {
        try {
            Long removed = redisTemplate.delete(keys);
            return removed == null ? 0 : removed.intValue();
        } catch (Exception exception) {
            log.warn("推荐缓存批量删除失败: size={}, error={}", keys.size(), exception.getMessage());
            return 0;
        }
    }

    public Map<String, Object> recommend(Long categoryId, int page, int pageSize) {
        int normalizedPage = Math.max(1, page);
        // 穿透防护：分区不存在时直接返回空页，不再回源
        if (categoryId != null && categoryId > 0 && !validCategoryIds().contains(categoryId)) {
            log.warn("推荐请求命中不存在的分区，直接返回空页: categoryId={}, page={}", categoryId, normalizedPage);
            return render(List.of(), 0L, normalizedPage, pageSize);
        }
        String cacheKey = KEY_PREFIX + (categoryId == null ? 0 : categoryId) + ":" + normalizedPage + ":" + pageSize;
        hotKeyDetector.record(cacheKey);

        RecommendCachePayload payload = localCache.get(cacheKey);
        if (payload != null) {
            cacheStats.localHit();
            return render(payload.items(), payload.total(), normalizedPage, pageSize);
        }
        payload = readCache(cacheKey);
        if (payload != null) {
            localCache.put(cacheKey, payload, properties.localTtlMs());
            extendTtlIfHot(cacheKey);
            return render(payload.items(), payload.total(), normalizedPage, pageSize);
        }
        payload = loadThroughMutex(cacheKey, categoryId, normalizedPage, pageSize);
        return render(payload.items(), payload.total(), normalizedPage, pageSize);
    }

    /**
     * 击穿防护：同一 key 只有抢到锁的线程回源并回填，其他线程等一小段时间后重读缓存；
     * 仍未命中才直接查库（不回填），避免大量线程同时写缓存。
     */
    private RecommendCachePayload loadThroughMutex(String cacheKey, Long categoryId, int page, int pageSize) {
        String lockKey = cacheKey + ":lock";
        String token = redisMutex.tryLock(lockKey, Duration.ofMillis(properties.lockTtlMs()));
        if (token != null) {
            try {
                RecommendCachePayload doubleChecked = readCache(cacheKey, false);
                if (doubleChecked != null) {
                    localCache.put(cacheKey, doubleChecked, properties.localTtlMs());
                    return doubleChecked;
                }
                return loadAndCache(cacheKey, categoryId, page, pageSize);
            } finally {
                redisMutex.unlock(lockKey, token);
            }
        }
        cacheStats.lockContended();
        sleepQuietly(properties.lockWaitMs());
        RecommendCachePayload retry = readCache(cacheKey, false);
        if (retry != null) {
            localCache.put(cacheKey, retry, properties.localTtlMs());
            return retry;
        }
        cacheStats.dbFallback();
        return new RecommendCachePayload(
                recommendationMapper.listRecommend(categoryId, pageSize, (page - 1) * pageSize),
                recommendationMapper.countRecommend(categoryId));
    }

    private RecommendCachePayload loadAndCache(String cacheKey, Long categoryId, int page, int pageSize) {
        cacheStats.dbLoad();
        List<RecommendSource> sources = recommendationMapper.listRecommend(categoryId, pageSize, (page - 1) * pageSize);
        long total = recommendationMapper.countRecommend(categoryId);
        RecommendCachePayload payload = new RecommendCachePayload(sources, total);
        localCache.put(cacheKey, payload, properties.localTtlMs());
        writeCache(cacheKey, payload);
        return payload;
    }

    private RecommendCachePayload readCache(String key) {
        return readCache(key, true);
    }

    /** countStats=false 用于锁内二次确认，避免同一次请求被重复计一次 miss */
    private RecommendCachePayload readCache(String key, boolean countStats) {
        if (System.currentTimeMillis() < redisDegradeUntil) {
            if (countStats) cacheStats.miss();
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                if (countStats) cacheStats.miss();
                return null;
            }
            RecommendCachePayload payload = objectMapper.readValue(cached, RecommendCachePayload.class);
            if (!countStats) return payload;
            if (payload.items().isEmpty()) cacheStats.nullHit();
            else cacheStats.redisHit();
            return payload;
        } catch (Exception exception) {
            enterDegrade();
            cacheStats.redisFailure();
            log.warn("推荐缓存读取失败，进入降级窗口: key={}, error={}", key, exception.getMessage());
            return null;
        }
    }

    private void writeCache(String key, RecommendCachePayload payload) {
        if (System.currentTimeMillis() < redisDegradeUntil) return;
        try {
            // 空结果用短 TTL 空值缓存，既阻挡穿透又不会长时间掩盖新数据
            int ttl = payload.items().isEmpty() ? properties.nullTtlSeconds() : ttlWithJitter(key);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), Duration.ofSeconds(ttl));
        } catch (Exception exception) {
            enterDegrade();
            cacheStats.redisFailure();
            log.warn("推荐缓存写入失败: key={}, error={}", key, exception.getMessage());
        }
    }

    /** 基础 TTL + 随机抖动，避免同一批 key 同时过期造成雪崩；热点 key 追加额外 TTL */
    private int ttlWithJitter(String key) {
        int ttl = properties.recommendTtlSeconds();
        if (hotKeyDetector.isHot(key)) ttl += properties.hotKeyExtraTtlSeconds();
        int jitter = properties.ttlJitterSeconds();
        return jitter <= 0 ? ttl : ttl + ThreadLocalRandom.current().nextInt(jitter + 1);
    }

    private void extendTtlIfHot(String key) {
        if (!hotKeyDetector.isHot(key) || !hotKeyDetector.markExtended(key)) return;
        if (System.currentTimeMillis() < redisDegradeUntil) return;
        try {
            redisTemplate.expire(key, Duration.ofSeconds(ttlWithJitter(key)));
        } catch (Exception exception) {
            log.warn("热点 key 续期失败: key={}, error={}", key, exception.getMessage());
        }
    }

    private void enterDegrade() {
        redisDegradeUntil = System.currentTimeMillis() + REDIS_DEGRADE_MS;
    }

    /**
     * 分区白名单：缓存于本地 60 秒 + Redis 5 分钟，避免任意 categoryId 都打到数据库。
     */
    private Set<Long> validCategoryIds() {
        long now = System.currentTimeMillis();
        if (!categoryIdCache.isEmpty() && now - categoryLoadedAt < CATEGORY_LOCAL_TTL_MS) return categoryIdCache;
        if (now >= redisDegradeUntil) {
            try {
                String cached = redisTemplate.opsForValue().get(CATEGORY_KEY);
                if (cached != null) {
                    List<Long> ids = objectMapper.readValue(cached, new TypeReference<List<Long>>() { });
                    Set<Long> set = Set.copyOf(ids);
                    categoryIdCache = set;
                    categoryLoadedAt = now;
                    return set;
                }
            } catch (Exception exception) {
                enterDegrade();
                log.warn("分区白名单缓存读取失败，回源数据库: {}", exception.getMessage());
            }
        }
        Set<Long> ids = recommendationMapper.listCategories().stream()
                .map(Category::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        categoryIdCache = ids;
        categoryLoadedAt = now;
        if (!ids.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(CATEGORY_KEY,
                        objectMapper.writeValueAsString(List.copyOf(ids)),
                        Duration.ofSeconds(properties.categoryCacheSeconds()));
            } catch (Exception exception) {
                log.warn("分区白名单缓存写入失败: {}", exception.getMessage());
            }
        }
        return ids;
    }

    private Map<String, Object> render(List<RecommendSource> sources, long total, int page, int pageSize) {
        List<RecommendItem> items = sources.stream().map(source -> new RecommendItem(source.id(), source.title(), source.authorName(),
                source.coverObjectKey() == null || source.coverObjectKey().isBlank() ? null : storageService.presignedReadUrl(source.coverObjectKey()),
                source.durationSeconds(), source.viewCount(), source.likeCount(), source.commentCount(), source.categoryId())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("hasMore", (long) page * pageSize < total);
        return result;
    }

    private void sleepQuietly(int millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Object> search(String keyword, int page, int pageSize) {
        // 交给 Elasticsearch 搜索服务，索引不可用时它会自动降级为 SQL 查询
        return videoSearchService.search(keyword, page, pageSize);
    }
}
