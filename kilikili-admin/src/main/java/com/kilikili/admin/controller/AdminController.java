package com.kilikili.admin.controller;

import com.kilikili.admin.dto.StatusRequest;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminService;
import com.kilikili.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "后台管理")
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final AdminAuthService adminAuthService;
    public AdminController(AdminService adminService, AdminAuthService adminAuthService) { this.adminService = adminService; this.adminAuthService = adminAuthService; }
    private void require(String authorization) { adminAuthService.requireAdmin(authorization); }
    @Operation(summary = "获取管理看板统计") @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestHeader("Authorization") String authorization) {
        require(authorization);
        return ApiResponse.ok(adminService.dashboard());
    }
    @Operation(summary = "查询用户") @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "50") int limit) {
        require(authorization);
        return ApiResponse.ok(adminService.users(keyword, Math.min(Math.max(limit, 1), 100)));
    }
    @Operation(summary = "启用或禁用用户") @PatchMapping("/users/{id}/status")
    public ApiResponse<Void> userStatus(@RequestHeader("Authorization") String authorization, @PathVariable long id, @Valid @RequestBody StatusRequest request) {
        require(authorization);
        adminService.updateUserStatus(id, request.status()); return ApiResponse.ok(null);
    }
    @Operation(summary = "查询视频及审核状态") @GetMapping("/videos")
    public ApiResponse<List<Map<String, Object>>> videos(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String status, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "50") int limit) {
        require(authorization);
        return ApiResponse.ok(adminService.videos(status, keyword, Math.min(Math.max(limit, 1), 100)));
    }
    @Operation(summary = "更新视频审核状态") @PatchMapping("/videos/{id}/status")
    public ApiResponse<Void> videoStatus(@RequestHeader("Authorization") String authorization, @PathVariable long id, @Valid @RequestBody StatusRequest request) {
        require(authorization); adminService.updateVideoStatus(id, request.status());
        return ApiResponse.ok(null);
    }
}
