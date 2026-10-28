package com.kilikili.web.controller;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.auth.AuthService;
import com.kilikili.web.service.CreatorDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "创作中心数据看板")
@RequestMapping("/api/creator")
public class CreatorDashboardController {
    private final CreatorDashboardService dashboardService;
    private final AuthService authService;

    public CreatorDashboardController(CreatorDashboardService dashboardService, AuthService authService) {
        this.dashboardService = dashboardService;
        this.authService = authService;
    }

    @Operation(summary = "获取创作中心数据看板")
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestHeader("Authorization") String authorization,
                                                      @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(dashboardService.dashboard(authService.requireUserId(authorization), days == 30 ? 30 : 7));
    }
}
