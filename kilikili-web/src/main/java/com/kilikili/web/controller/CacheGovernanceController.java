package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.cache.CacheGovernanceProperties;
import com.kilikili.web.cache.CacheStats;
import com.kilikili.web.cache.HotKeyDetector;
import com.kilikili.web.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存治理观测接口：查看推荐缓存命中率、热点 key 榜单，并支持手动失效缓存。
 */
@Tag(name = "缓存治理")
@RestController
@RequestMapping("/api/cache")
public class CacheGovernanceController {
    private final AuthService authService;
    private final CacheStats cacheStats;
    private final HotKeyDetector hotKeyDetector;
    private final CacheGovernanceProperties properties;
    private final RecommendationService recommendationService;

    public CacheGovernanceController(AuthService authService, CacheStats cacheStats, HotKeyDetector hotKeyDetector,
                                     CacheGovernanceProperties properties, RecommendationService recommendationService) {
        this.authService = authService;
        this.cacheStats = cacheStats;
        this.hotKeyDetector = hotKeyDetector;
        this.properties = properties;
        this.recommendationService = recommendationService;
    }

    @Operation(summary = "推荐缓存监控", description = "返回命中率、回源次数、锁竞争、Redis 降级次数与当前热点 key")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam(defaultValue = "10") int hotKeyLimit) {
        authService.requireUserId(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", cacheStats.snapshot());
        result.put("hotKeys", hotKeyDetector.topHotKeys(hotKeyLimit));
        result.put("hotKeyWindow", hotKeyDetector.localWindowSnapshot());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("recommendTtlSeconds", properties.recommendTtlSeconds());
        config.put("ttlJitterSeconds", properties.ttlJitterSeconds());
        config.put("nullTtlSeconds", properties.nullTtlSeconds());
        config.put("localTtlMs", properties.localTtlMs());
        config.put("lockTtlMs", properties.lockTtlMs());
        config.put("doubleDeleteDelayMs", properties.doubleDeleteDelayMs());
        config.put("hotKeyThreshold", properties.hotKeyThreshold());
        result.put("config", config);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "热点 key 榜单")
    @GetMapping("/hot-keys")
    public ApiResponse<Map<String, Object>> hotKeys(@RequestHeader("Authorization") String authorization,
                                                   @RequestParam(defaultValue = "20") int limit) {
        authService.requireUserId(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", hotKeyDetector.topHotKeys(limit));
        result.put("window", hotKeyDetector.localWindowSnapshot());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "手动失效推荐缓存", description = "执行延迟双删，用于验证缓存一致性")
    @PostMapping("/evict")
    public ApiResponse<Map<String, Object>> evict(@RequestHeader("Authorization") String authorization) {
        authService.requireUserId(authorization);
        recommendationService.evictRecommendCache();
        return ApiResponse.ok(Map.of("message", "推荐缓存已执行延迟双删",
                "delayMs", properties.doubleDeleteDelayMs()));
    }
}
