package com.kilikili.admin.config;

import com.kilikili.admin.entity.AdminAccount;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminOperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AdminOperationLogInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = AdminOperationLogInterceptor.class.getName() + ".startTime";
    private static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("/dashboard", "仪表盘"),
            Map.entry("/videos", "视频管理"),
            Map.entry("/categories", "分类管理"),
            Map.entry("/danmakus", "弹幕管理"),
            Map.entry("/comments", "评论管理"),
            Map.entry("/users", "用户管理"),
            Map.entry("/system-settings", "系统设置"),
            Map.entry("/operation-logs", "操作日志")
    );

    private final AdminOperationLogService adminOperationLogService;
    private final AdminAuthService adminAuthService;

    public AdminOperationLogInterceptor(AdminOperationLogService adminOperationLogService,
                                        AdminAuthService adminAuthService) {
        this.adminOperationLogService = adminOperationLogService;
        this.adminAuthService = adminAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        try {
            AdminAccount account = adminAuthService.currentAdminOrNull(request.getHeader("Authorization"));
            String module = resolveModule(request.getRequestURI());
            String action = resolveAction(request.getMethod(), request.getRequestURI());
            long durationMs = elapsedMillis(request.getAttribute(START_TIME_ATTRIBUTE));
            boolean success = exception == null && response.getStatus() < 400;
            String errorMessage = success ? null : truncate(exception == null
                    ? "HTTP " + response.getStatus()
                    : exception.getMessage(), 500);
            adminOperationLogService.record(
                    account == null ? null : account.id(),
                    account == null ? "未知管理员" : account.nickname(),
                    module,
                    action,
                    module + " - " + action,
                    clientIp(request),
                    success ? "SUCCESS" : "FAILURE",
                    errorMessage,
                    durationMs
            );
        } catch (Exception logException) {
            log.error("记录管理员操作日志失败: path={}", request.getRequestURI(), logException);
        }
    }

    private String resolveModule(String uri) {
        String path = uri.startsWith("/api/admin") ? uri.substring("/api/admin".length()) : uri;
        return MODULES.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("系统管理");
    }

    private String resolveAction(String method, String uri) {
        if ("DELETE".equals(method) && uri.endsWith("/operation-logs")) return "清空";
        return switch (method) {
            case "GET" -> "查询";
            case "POST" -> "新增";
            case "PUT" -> "编辑";
            case "PATCH" -> uri.endsWith("/status") ? "状态变更" : "修改";
            case "DELETE" -> "删除";
            default -> method;
        };
    }

    private long elapsedMillis(Object startedAt) {
        return startedAt instanceof Long start ? Math.max(0, (System.nanoTime() - start) / 1_000_000) : 0;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return truncate(forwarded.split(",")[0].trim(), 64);
        String realIp = request.getHeader("X-Real-IP");
        return truncate(realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp, 64);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
