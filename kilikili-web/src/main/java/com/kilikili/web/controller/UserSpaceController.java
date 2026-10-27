package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.service.UserSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "UP 主主页")
@RequestMapping("/api/users")
public class UserSpaceController {
    private final UserSpaceService userSpaceService;
    private final AuthService authService;

    public UserSpaceController(UserSpaceService userSpaceService, AuthService authService) {
        this.userSpaceService = userSpaceService;
        this.authService = authService;
    }

    @Operation(summary = "获取 UP 主主页信息与投稿视频")
    @GetMapping("/{id}/space")
    public ApiResponse<Map<String, Object>> space(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @PathVariable long id,
                                                  @RequestParam(defaultValue = "latest") String sort,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "12") int pageSize) {
        Long viewerId = null;
        if (authorization != null && !authorization.isBlank()) {
            try {
                viewerId = authService.requireUserId(authorization);
            } catch (RuntimeException ignored) {
                // 未登录或登录态失效时仍允许浏览主页
            }
        }
        return ApiResponse.ok(userSpaceService.space(id, viewerId, "popular".equals(sort) ? "popular" : "latest",
                Math.max(1, page), Math.min(Math.max(1, pageSize), 30)));
    }
}
