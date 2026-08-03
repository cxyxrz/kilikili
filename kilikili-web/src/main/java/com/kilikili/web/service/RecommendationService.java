package com.kilikili.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.Category;
import com.kilikili.web.entity.RecommendItem;
import com.kilikili.web.entity.RecommendSource;
import com.kilikili.web.mapper.RecommendationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private final RecommendationMapper recommendationMapper;
    private final StorageService storageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    public RecommendationService(RecommendationMapper recommendationMapper, StorageService storageService, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.recommendationMapper = recommendationMapper; this.storageService = storageService; this.redisTemplate = redisTemplate; this.objectMapper = objectMapper;
    }
    public List<Category> categories() { return recommendationMapper.listCategories(); }
    public Map<String, Object> recommend(Long categoryId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String cacheKey = "kilikili:recommend:" + (categoryId == null ? 0 : categoryId) + ":" + page + ":" + pageSize;
        List<RecommendSource> sources = readCache(cacheKey);
        if (sources == null) {
            sources = recommendationMapper.listRecommend(categoryId, pageSize, offset);
            writeCache(cacheKey, sources);
        }
        long total = recommendationMapper.countRecommend(categoryId);
        List<RecommendItem> items = sources.stream().map(source -> new RecommendItem(source.id(), source.title(), source.authorName(),
                source.coverObjectKey() == null || source.coverObjectKey().isBlank() ? null : storageService.presignedReadUrl(source.coverObjectKey()),
                source.durationSeconds(), source.viewCount(), source.likeCount(), source.commentCount(), source.categoryId())).toList();
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total, "hasMore", (long) page * pageSize < total);
    }
    private List<RecommendSource> readCache(String key) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            return cached == null ? null : objectMapper.readValue(cached, new TypeReference<List<RecommendSource>>() { });
        } catch (Exception exception) { log.warn("推荐缓存读取失败: key={}", key, exception); return null; }
    }
    private void writeCache(String key, List<RecommendSource> sources) {
        try { redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(sources), CACHE_TTL); }
        catch (Exception exception) { log.warn("推荐缓存写入失败: key={}", key, exception); }
    }
}
