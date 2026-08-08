package com.kilikili.admin.controller;

import com.kilikili.admin.dto.AdminCategoryRequest;
import com.kilikili.admin.entity.AdminCategoryItem;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminCategoryService;
import com.kilikili.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "后台分类管理")
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService adminCategoryService;
    private final AdminAuthService adminAuthService;

    public AdminCategoryController(AdminCategoryService adminCategoryService, AdminAuthService adminAuthService) {
        this.adminCategoryService = adminCategoryService;
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "查询全部分类及统计数据")
    @GetMapping
    public ApiResponse<List<AdminCategoryItem>> list(@RequestHeader("Authorization") String authorization) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminCategoryService.list());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public ApiResponse<AdminCategoryItem> create(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AdminCategoryRequest request) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminCategoryService.create(request));
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/{id}")
    public ApiResponse<AdminCategoryItem> update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long id,
            @Valid @RequestBody AdminCategoryRequest request) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminCategoryService.update(id, request));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long id) {
        adminAuthService.requireAdmin(authorization);
        adminCategoryService.delete(id);
        return ApiResponse.ok(null);
    }
}
