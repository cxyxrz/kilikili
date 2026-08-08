package com.kilikili.admin.service;

import com.kilikili.admin.entity.AdminUserItem;
import com.kilikili.admin.entity.AdminUserPage;
import com.kilikili.admin.mapper.AdminUserMapper;
import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AdminUserService {
    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final AdminUserMapper adminUserMapper;
    private final StorageService storageService;

    public AdminUserService(AdminUserMapper adminUserMapper, StorageService storageService) {
        this.adminUserMapper = adminUserMapper;
        this.storageService = storageService;
    }

    public AdminUserPage list(String keyword, String status, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalizedStatus != null && !USER_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException("不支持的用户状态");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        long total = adminUserMapper.count(normalizedKeyword, normalizedStatus);
        List<AdminUserItem> items = offset >= total ? List.of() : adminUserMapper
                .list(normalizedKeyword, normalizedStatus, offset, normalizedPageSize)
                .stream()
                .map(source -> new AdminUserItem(
                        source.id(),
                        source.username(),
                        source.email(),
                        source.nickname(),
                        source.avatar() == null || source.avatar().isBlank()
                                ? null
                                : storageService.presignedReadUrl(source.avatar()),
                        source.gender(),
                        source.status(),
                        source.createdAt(),
                        source.lastLoginAt()
                ))
                .toList();
        return new AdminUserPage(items, total, normalizedPage, normalizedPageSize, offset + items.size() < total);
    }

    @Transactional
    public void updateStatus(long id, String status) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!USER_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException("不支持的用户状态");
        }
        if (adminUserMapper.updateStatus(id, normalizedStatus) == 0) {
            throw new BusinessException("普通用户不存在");
        }
        log.info("管理员更新用户状态: userId={}, status={}", id, normalizedStatus);
    }
}
