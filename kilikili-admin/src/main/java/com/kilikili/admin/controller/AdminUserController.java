package com.kilikili.admin.controller;

import com.kilikili.admin.dto.StatusRequest;
import com.kilikili.admin.entity.AdminUserPage;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminUserService;
import com.kilikili.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "后台用户管理")
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final AdminAuthService adminAuthService;

    public AdminUserController(AdminUserService adminUserService, AdminAuthService adminAuthService) {
        this.adminUserService = adminUserService;
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "分页查询普通用户")
    @GetMapping
    public ApiResponse<AdminUserPage> list(
            @RequestHeader("Authorization") String authorization,
            @Parameter(description = "昵称、账号或邮箱") @RequestParam(required = false) String keyword,
            @Parameter(description = "ACTIVE 正常，DISABLED 禁用") @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminUserService.list(keyword, status, page, pageSize));
    }

    @Operation(summary = "启用或禁用普通用户")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request) {
        adminAuthService.requireAdmin(authorization);
        adminUserService.updateStatus(id, request.status());
        return ApiResponse.ok(null);
    }
}
