package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.dto.CommentRequest;
import com.kilikili.web.dto.ReplyRequest;
import com.kilikili.web.dto.DanmakuRequest;
import com.kilikili.web.entity.Danmaku;
import com.kilikili.web.entity.Video;
import com.kilikili.web.entity.VideoComment;
import com.kilikili.web.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@Tag(name = "视频互动")
@RequestMapping("/api/videos")
public class VideoController {
    private final VideoService videoService;
    private final AuthService authService;
    public VideoController(VideoService videoService, AuthService authService) { this.videoService = videoService; this.authService = authService; }
    @Operation(summary = "获取已发布视频列表") @GetMapping public ApiResponse<List<Video>> list()
    {
        return ApiResponse.ok(videoService.list());
    }
    @Operation(summary = "获取视频详情和播放地址") @GetMapping("/{id}") public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.ok(videoService.detail(id));
    }
    @Operation(summary = "记录观看历史") @PostMapping("/{id}/history")
    public ApiResponse<Void> recordHistory(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        videoService.recordHistory(authService.requireUserId(authorization), id);
        return ApiResponse.ok(null);
    }
    @Operation(summary = "获取观看历史") @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(@RequestHeader("Authorization") String authorization,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(videoService.history(authService.requireUserId(authorization), Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }
    @Operation(summary = "删除单条观看历史") @DeleteMapping("/history/{videoId}")
    public ApiResponse<Void> deleteHistory(@RequestHeader("Authorization") String authorization, @PathVariable long videoId) {
        videoService.deleteHistory(authService.requireUserId(authorization), videoId);
        return ApiResponse.ok(null);
    }
    @Operation(summary = "清空观看历史") @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory(@RequestHeader("Authorization") String authorization) {
        videoService.clearHistory(authService.requireUserId(authorization));
        return ApiResponse.ok(null);
    }
    @Operation(summary = "获取当前用户的视频管理列表") @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> mine(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(videoService.mine(authService.requireUserId(authorization), keyword, status, Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }
    @Operation(summary = "获取当前用户创作数据") @GetMapping("/mine/stats")
    public ApiResponse<Map<String, Object>> creatorStats(@RequestHeader("Authorization") String authorization) { return ApiResponse.ok(videoService.creatorStats(authService.requireUserId(authorization))); }
    @Operation(summary = "上传视频", description = "可选上传封面、分区和时长；上传后进入待审核状态")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> upload(@RequestHeader("Authorization") String authorization,
                                                     @RequestParam @NotBlank @Size(max = 80) String title,
                                                     @RequestParam(required = false, defaultValue = "") @Size(max = 2000) String description,
                                                     @RequestParam(defaultValue = "1") long categoryId,
                                                     @RequestParam(defaultValue = "0") int durationSeconds,
                                                     @RequestParam(defaultValue = "ORIGINAL") String creationDeclaration,
                                                     @RequestPart MultipartFile cover,
                                                     @RequestPart MultipartFile file) {
        return ApiResponse.ok(videoService.upload(authService.requireUserId(authorization), title, description, categoryId, durationSeconds, creationDeclaration, cover, file));
    }
    @Operation(summary = "点赞或取消点赞") @PostMapping("/{id}/like") public ApiResponse<Map<String, Object>> like(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(videoService.toggleLike(authService.requireUserId(authorization), id));
    }
    @Operation(summary = "收藏或取消收藏") @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(videoService.toggleFavorite(authService.requireUserId(authorization), id));
    }
    @Operation(summary = "查询收藏状态") @GetMapping("/{id}/favorite-status")
    public ApiResponse<Map<String, Object>> favoriteStatus(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(Map.of("favorited", videoService.isFavorited(authService.requireUserId(authorization), id)));
    }
    @Operation(summary = "获取我的收藏") @GetMapping("/favorites")
    public ApiResponse<Map<String, Object>> favorites(@RequestHeader("Authorization") String authorization,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(videoService.favorites(authService.requireUserId(authorization), Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }
    @Operation(summary = "获取视频评论") @GetMapping("/{id}/comments") public ApiResponse<List<VideoComment>> comments(@PathVariable long id) {
        return ApiResponse.ok(videoService.comments(id));
    }
    @Operation(summary = "获取视频弹幕") @GetMapping("/{id}/danmakus")
    public ApiResponse<List<Danmaku>> danmakus(@PathVariable long id) { return ApiResponse.ok(videoService.danmakus(id)); }
    @Operation(summary = "发送视频弹幕") @PostMapping("/{id}/danmakus")
    public ApiResponse<Void> sendDanmaku(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                         @Valid @RequestBody DanmakuRequest request) {
        videoService.sendDanmaku(authService.requireUserId(authorization), id, request.content(), request.mode(), request.color(), request.timeMs());
        return ApiResponse.ok(null);
    }
    @Operation(summary = "发布视频评论") @PostMapping("/{id}/comments") public ApiResponse<Void> comment(@RequestHeader("Authorization") String authorization, @PathVariable long id, @Valid @RequestBody CommentRequest request) {
        videoService.comment(authService.requireUserId(authorization), id, request.content()); return ApiResponse.ok(null);
    }
    @Operation(summary = "回复视频评论") @PostMapping("/{id}/comments/{commentId}/replies")
    public ApiResponse<Void> reply(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                   @PathVariable long commentId, @Valid @RequestBody ReplyRequest request) {
        videoService.reply(authService.requireUserId(authorization), id, commentId, request.content());
        return ApiResponse.ok(null);
    }
}
