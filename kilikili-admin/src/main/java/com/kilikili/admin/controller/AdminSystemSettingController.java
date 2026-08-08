package com.kilikili.admin.controller;

import com.kilikili.admin.entity.AdminAccount;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.setting.SystemSettingService;
import com.kilikili.common.setting.SystemSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "后台系统设置")
@RequestMapping("/api/admin/system-settings")
public class AdminSystemSettingController {
    private final SystemSettingService systemSettingService;
    private final AdminAuthService adminAuthService;

    public AdminSystemSettingController(SystemSettingService systemSettingService,
                                        AdminAuthService adminAuthService) {
        this.systemSettingService = systemSettingService;
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "获取系统功能设置")
    @GetMapping
    public ApiResponse<SystemSettings> get(@RequestHeader("Authorization") String authorization) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(systemSettingService.current());
    }

    @Operation(summary = "保存系统功能设置")
    @PutMapping
    public ApiResponse<SystemSettings> save(
            @RequestHeader("Authorization") String authorization,
            @RequestBody SystemSettings settings) {
        AdminAccount admin = adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(systemSettingService.save(settings, admin.id()));
    }
}
