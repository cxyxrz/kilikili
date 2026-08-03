package com.kilikili.admin.controller;

import com.kilikili.admin.dto.AdminLoginRequest;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Tag(name = "管理员认证")
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminAuthService adminAuthService;
    public AdminAuthController(AdminAuthService adminAuthService) { this.adminAuthService = adminAuthService; }
    @Operation(summary = "管理员账号密码登录")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody AdminLoginRequest request) { return ApiResponse.ok(adminAuthService.login(request)); }
}
