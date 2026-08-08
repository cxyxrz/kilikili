package com.kilikili.admin.controller;

import com.kilikili.admin.entity.AdminDanmakuPage;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminDanmakuService;
import com.kilikili.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "后台弹幕管理")
@RequestMapping("/api/admin/danmakus")
public class AdminDanmakuController {
    private final AdminDanmakuService adminDanmakuService;
    private final AdminAuthService adminAuthService;

    public AdminDanmakuController(AdminDanmakuService adminDanmakuService, AdminAuthService adminAuthService) {
        this.adminDanmakuService = adminDanmakuService;
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "分页查询弹幕")
    @GetMapping
    public ApiResponse<AdminDanmakuPage> list(
            @RequestHeader("Authorization") String authorization,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量，最大 100") @RequestParam(defaultValue = "10") int pageSize) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminDanmakuService.list(page, pageSize));
    }

    @Operation(summary = "删除弹幕")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader("Authorization") String authorization,
            @Parameter(description = "弹幕 ID") @PathVariable long id) {
        adminAuthService.requireAdmin(authorization);
        adminDanmakuService.delete(id);
        return ApiResponse.ok(null);
    }
}
