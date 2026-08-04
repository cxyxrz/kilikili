package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "关注关系")
@RequestMapping("/api/users")
public class FollowController {
    private final FollowService followService;
    private final AuthService authService;

    public FollowController(FollowService followService, AuthService authService) {
        this.followService = followService;
        this.authService = authService;
    }

    @Operation(summary = "关注或取消关注 UP 主")
    @PostMapping("/{id}/follow")
    public ApiResponse<Map<String, Object>> toggle(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(followService.toggle(authService.requireUserId(authorization), id));
    }

    @Operation(summary = "查询是否关注 UP 主")
    @GetMapping("/{id}/follow-status")
    public ApiResponse<Map<String, Object>> status(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return ApiResponse.ok(Map.of("following", followService.isFollowing(authService.requireUserId(authorization), id)));
    }

    @Operation(summary = "获取我的粉丝列表")
    @GetMapping("/me/followers")
    public ApiResponse<Map<String, Object>> followers(@RequestHeader("Authorization") String authorization,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(followService.followers(authService.requireUserId(authorization), Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }

    @Operation(summary = "获取我关注 UP 主发布的视频")
    @GetMapping("/me/following/videos")
    public ApiResponse<Map<String, Object>> followingVideos(@RequestHeader("Authorization") String authorization,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(followService.followingVideos(authService.requireUserId(authorization), Math.max(1, page), Math.min(Math.max(1, pageSize), 50)));
    }
}
