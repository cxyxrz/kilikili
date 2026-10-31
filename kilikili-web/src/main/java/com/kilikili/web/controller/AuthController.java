package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.dto.LoginRequest;
import com.kilikili.web.dto.RegisterRequest;
import com.kilikili.web.dto.ProfileUpdateRequest;
import com.kilikili.web.entity.Account;
import com.kilikili.web.entity.AccountProfile;
import com.kilikili.web.service.AccountService;
import com.kilikili.web.security.RateLimit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "用户认证")
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accountService;
    private final AuthService authService;
    public AuthController(AccountService accountService, AuthService authService) {
        this.accountService = accountService; this.authService = authService;
    }
    @Operation(summary = "注册账号") @PostMapping("/register")
    @RateLimit(name = "auth:register", capacity = 5, refillPerSecond = 0.2, perUser = false)
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(accountService.register(request));
    }
    @Operation(summary = "用户登录") @PostMapping("/login")
    @RateLimit(name = "auth:login", capacity = 10, refillPerSecond = 0.5, perUser = false)
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(accountService.login(request));
    }
    @Operation(summary = "JWT 续期", description = "访问令牌临近过期时换取新令牌；短期令牌也可由响应头自动续期")
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String authorization) {
        String newToken = authService.renewIfNeeded(authorization).orElse(null);
        long userId = authService.requireUserId(authorization);
        if (newToken == null) {
            // 尚未到期：直接返回原令牌，前端无需替换
            return ApiResponse.ok(Map.of("renewed", false, "userId", userId,
                    "expiresHintMinutes", 0));
        }
        return ApiResponse.ok(Map.of("renewed", true, "token", newToken, "userId", userId));
    }
    @Operation(summary = "退出登录", description = "删除服务端会话，令牌立即失效")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return ApiResponse.ok(null);
    }
    @Operation(summary = "获取当前登录用户") @GetMapping("/me")
    public ApiResponse<Account> me(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(accountService.current(authService.requireUserId(authorization)));
    }
    @Operation(summary = "获取当前用户资料") @GetMapping("/me/profile")
    public ApiResponse<AccountProfile> profile(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(accountService.profile(authService.requireUserId(authorization)));
    }
    @Operation(summary = "更新当前用户资料") @PatchMapping("/me/profile")
    public ApiResponse<AccountProfile> updateProfile(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(accountService.updateProfile(authService.requireUserId(authorization), request));
    }
    @Operation(summary = "上传当前用户头像") @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    public ApiResponse<AccountProfile> uploadAvatar(@RequestHeader("Authorization") String authorization, @RequestPart MultipartFile file) {
        return ApiResponse.ok(accountService.uploadAvatar(authService.requireUserId(authorization), file));
    }
}
