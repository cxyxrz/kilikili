package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.web.entity.Category;
import com.kilikili.web.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "推荐与分区")
@RequestMapping("/api")
public class RecommendationController {
    private final RecommendationService recommendationService;
    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }
    @Operation(summary = "获取推荐分区") @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(recommendationService.categories());
    }
    @Operation(summary = "获取热度推荐视频流") @GetMapping("/recommendations")
    public ApiResponse<Map<String, Object>> recommendations(@RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(1, page); int safeSize = Math.min(Math.max(1, pageSize), 40);
        return ApiResponse.ok(recommendationService.recommend(categoryId, safePage, safeSize));
    }
    @Operation(summary = "搜索视频和 UP 主") @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam String keyword, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(1, page); int safeSize = Math.min(Math.max(1, pageSize), 40);
        return ApiResponse.ok(recommendationService.search(keyword, safePage, safeSize));
    }
    @Operation(summary = "搜索联想词") @GetMapping("/search/suggest")
    public ApiResponse<List<String>> suggest(@RequestParam String keyword) {
        return ApiResponse.ok(recommendationService.suggest(keyword));
    }
    @Operation(summary = "热搜榜") @GetMapping("/search/hot")
    public ApiResponse<List<Map<String, Object>>> hot() {
        return ApiResponse.ok(recommendationService.hotKeywords());
    }
}
