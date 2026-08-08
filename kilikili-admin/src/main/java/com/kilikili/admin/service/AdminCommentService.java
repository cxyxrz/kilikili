package com.kilikili.admin.service;

import com.kilikili.admin.entity.AdminCommentItem;
import com.kilikili.admin.entity.AdminCommentPage;
import com.kilikili.admin.entity.AdminCommentTarget;
import com.kilikili.admin.mapper.AdminCommentMapper;
import com.kilikili.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AdminCommentService {
    private static final Logger log = LoggerFactory.getLogger(AdminCommentService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> COMMENT_TYPES = Set.of("ROOT", "REPLY");

    private final AdminCommentMapper adminCommentMapper;

    public AdminCommentService(AdminCommentMapper adminCommentMapper) {
        this.adminCommentMapper = adminCommentMapper;
    }

    public AdminCommentPage list(String keyword, String type, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        if (normalizedType != null && !COMMENT_TYPES.contains(normalizedType)) {
            throw new BusinessException("不支持的评论类型");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        long total = adminCommentMapper.count(normalizedKeyword, normalizedType);
        List<AdminCommentItem> items = offset >= total
                ? List.of()
                : adminCommentMapper.list(normalizedKeyword, normalizedType, offset, normalizedPageSize);
        return new AdminCommentPage(
                items,
                total,
                normalizedPage,
                normalizedPageSize,
                offset + items.size() < total
        );
    }

    @Transactional
    public void delete(long id) {
        AdminCommentTarget target = adminCommentMapper.findTarget(id);
        if (target == null) {
            throw new BusinessException("评论不存在或已被删除");
        }
        int deleted = target.parentId() == null
                ? adminCommentMapper.deleteThread(id)
                : adminCommentMapper.deleteOne(id);
        if (deleted == 0) {
            throw new BusinessException("评论不存在或已被删除");
        }
        adminCommentMapper.decreaseVideoCommentCount(target.videoId(), deleted);
        log.info("管理员删除评论: commentId={}, videoId={}, deletedCount={}", id, target.videoId(), deleted);
    }
}
