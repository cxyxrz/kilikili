package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "消息通知")
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @Operation(summary = "获取我的消息通知")
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "30") int pageSize) {
        return ApiResponse.ok(notificationService.list(authService.requireUserId(authorization), type, Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }

    @Operation(summary = "获取未读消息数量")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(authService.requireUserId(authorization))));
    }

    @Operation(summary = "全部消息标记已读")
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestHeader("Authorization") String authorization) {
        notificationService.markAllRead(authService.requireUserId(authorization));
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除消息通知")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        notificationService.delete(authService.requireUserId(authorization), id);
        return ApiResponse.ok(null);
    }
}
