package com.kilikili.admin.controller;

import com.kilikili.admin.entity.AdminCommentPage;
import com.kilikili.admin.service.AdminAuthService;
import com.kilikili.admin.service.AdminCommentService;
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
@Tag(name = "后台评论管理")
@RequestMapping("/api/admin/comments")
public class AdminCommentController {
    private final AdminCommentService adminCommentService;
    private final AdminAuthService adminAuthService;

    public AdminCommentController(AdminCommentService adminCommentService, AdminAuthService adminAuthService) {
        this.adminCommentService = adminCommentService;
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "分页查询评论")
    @GetMapping
    public ApiResponse<AdminCommentPage> list(
            @RequestHeader("Authorization") String authorization,
            @Parameter(description = "评论内容、用户或视频标题") @RequestParam(required = false) String keyword,
            @Parameter(description = "ROOT 主评论，REPLY 回复") @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        adminAuthService.requireAdmin(authorization);
        return ApiResponse.ok(adminCommentService.list(keyword, type, page, pageSize));
    }

    @Operation(summary = "删除评论或回复")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long id) {
        adminAuthService.requireAdmin(authorization);
        adminCommentService.delete(id);
        return ApiResponse.ok(null);
    }
}
