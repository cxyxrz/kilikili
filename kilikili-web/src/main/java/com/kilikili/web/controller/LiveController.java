package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.dto.LiveMessageRequest;
import com.kilikili.web.service.LiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@Validated
@Tag(name = "直播")
@RequestMapping("/api/live")
public class LiveController {
    private final LiveService liveService;
    private final AuthService authService;

    public LiveController(LiveService liveService, AuthService authService) {
        this.liveService = liveService;
        this.authService = authService;
    }

    @Operation(summary = "直播列表", description = "按直播中、待开播、已结束排序")
    @GetMapping("/rooms")
    public ApiResponse<List<Map<String, Object>>> rooms() {
        return ApiResponse.ok(liveService.rooms());
    }

    @Operation(summary = "我的直播间", description = "返回最近创建的直播间，没有则为 null")
    @GetMapping("/rooms/mine")
    public ApiResponse<Map<String, Object>> mine(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(liveService.mine(authService.requireUserId(authorization)));
    }

    @Operation(summary = "创建或更新直播间", description = "生成 RTMP 推流密钥；待开播的房间会直接更新标题和封面")
    @PostMapping(value = "/rooms", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> create(@RequestHeader("Authorization") String authorization,
                                                    @RequestParam @NotBlank @Size(max = 80) String title,
                                                    @RequestPart(required = false) MultipartFile cover) {
        return ApiResponse.ok(liveService.create(authService.requireUserId(authorization), title, cover));
    }

    @Operation(summary = "直播间详情", description = "包含最近 50 条弹幕；主播本人可见推流地址")
    @GetMapping("/rooms/{id}")
    public ApiResponse<Map<String, Object>> detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @PathVariable long id) {
        return ApiResponse.ok(liveService.detail(optionalUserId(authorization), id));
    }

    @Operation(summary = "手动开播", description = "用于不走 SRS 回调时强制切换直播状态")
    @PostMapping("/rooms/{id}/start")
    public ApiResponse<Map<String, Object>> start(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(liveService.start(authService.requireUserId(authorization), id));
    }

    @Operation(summary = "下播")
    @PostMapping("/rooms/{id}/stop")
    public ApiResponse<Map<String, Object>> stop(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(liveService.stop(authService.requireUserId(authorization), id));
    }

    @Operation(summary = "获取直播弹幕")
    @GetMapping("/rooms/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> messages(@PathVariable long id) {
        return ApiResponse.ok(liveService.messages(id));
    }

    @Operation(summary = "发送直播弹幕", description = "写入数据库并通过 /ws/live 实时广播")
    @PostMapping("/rooms/{id}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(@RequestHeader("Authorization") String authorization,
                                                         @PathVariable long id,
                                                         @Valid @RequestBody LiveMessageRequest request) {
        return ApiResponse.ok(liveService.sendMessage(authService.requireUserId(authorization), id, request.content()));
    }

    @Operation(summary = "直播 HLS 代理", description = "转发 SRS 的 HLS 播放列表与切片，并隐藏推流密钥")
    @GetMapping("/rooms/{id}/hls/{file}")
    public ResponseEntity<byte[]> hls(@PathVariable long id, @PathVariable String file) {
        byte[] body = liveService.hls(id, file);
        boolean playlist = file.endsWith(".m3u8");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(playlist ? "application/vnd.apple.mpegurl" : "video/mp2t"))
                .cacheControl(playlist ? CacheControl.noStore() : CacheControl.maxAge(2, TimeUnit.SECONDS))
                .body(body);
    }

    @Operation(summary = "SRS 推流鉴权回调", description = "推流密钥合法时返回 0 允许推流")
    @PostMapping("/hooks/publish")
    public ResponseEntity<String> onPublish(@RequestBody Map<String, Object> payload) {
        return hookResponse(liveService.onPublish(payload));
    }

    @Operation(summary = "SRS 断开推流回调")
    @PostMapping("/hooks/unpublish")
    public ResponseEntity<String> onUnpublish(@RequestBody Map<String, Object> payload) {
        return hookResponse(liveService.onUnpublish(payload));
    }

    private ResponseEntity<String> hookResponse(int code) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(String.valueOf(code));
    }

    /** 详情接口允许游客访问，只有主播本人才能拿到推流密钥 */
    private long optionalUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return 0;
        try {
            return authService.requireUserId(authorization);
        } catch (RuntimeException exception) {
            return 0;
        }
    }
}
