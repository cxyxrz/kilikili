package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.dto.SendMessageRequest;
import com.kilikili.web.service.PrivateMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "私信")
@RequestMapping("/api/messages")
public class PrivateMessageController {
    private final PrivateMessageService privateMessageService;
    private final AuthService authService;

    public PrivateMessageController(PrivateMessageService privateMessageService, AuthService authService) {
        this.privateMessageService = privateMessageService;
        this.authService = authService;
    }

    @Operation(summary = "获取我的私信会话列表")
    @GetMapping("/conversations")
    public ApiResponse<Map<String, Object>> conversations(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(privateMessageService.conversations(authService.requireUserId(authorization)));
    }

    @Operation(summary = "获取私信未读数量")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(Map.of("count", privateMessageService.unreadCount(authService.requireUserId(authorization))));
    }

    @Operation(summary = "获取与某个用户的私信记录")
    @GetMapping("/{peerId}")
    public ApiResponse<Map<String, Object>> thread(@RequestHeader("Authorization") String authorization,
                                                   @PathVariable long peerId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "30") int pageSize) {
        return ApiResponse.ok(privateMessageService.thread(authService.requireUserId(authorization), peerId,
                Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }

    @Operation(summary = "发送私信")
    @PostMapping
    public ApiResponse<Map<String, Object>> send(@RequestHeader("Authorization") String authorization,
                                                 @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(privateMessageService.send(authService.requireUserId(authorization), request.toId(), request.content()));
    }

    @Operation(summary = "将某个会话的私信标记为已读")
    @PostMapping("/{peerId}/read")
    public ApiResponse<Map<String, Object>> markRead(@RequestHeader("Authorization") String authorization,
                                                     @PathVariable long peerId) {
        return ApiResponse.ok(privateMessageService.markRead(authService.requireUserId(authorization), peerId));
    }
}
