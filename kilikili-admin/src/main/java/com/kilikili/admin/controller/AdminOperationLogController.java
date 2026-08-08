package com.kilikili.admin.controller;

import com.kilikili.admin.entity.AdminOperationLogPage;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminOperationLogService;
import com.kilikili.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "后台操作日志")
@RequestMapping("/api/admin/operation-logs")
public class AdminOperationLogController {
    private final AdminOperationLogService adminOperationLogService;
    private final AdminAuthService adminAuthService;

    public AdminOperationLogController(AdminOperationLogService adminOperationLogService,
                                       AdminAuthService adminAuthService) {
        this.adminOperationLogService = adminOperationLogService;
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "分页查询管理员操作日志")
    @GetMapping
    public ApiResponse<AdminOperationLogPage> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminOperationLogService.list(operator, module, page, pageSize));
    }

    @Operation(summary = "清空管理员操作日志")
    @DeleteMapping
    public ApiResponse<Map<String, Integer>> clear(@RequestHeader("Authorization") String authorization) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(Map.of("deleted", adminOperationLogService.clear()));
    }
}
