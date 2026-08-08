package com.kilikili.admin.service;

import com.kilikili.admin.entity.AdminDanmakuItem;
import com.kilikili.admin.entity.AdminDanmakuPage;
import com.kilikili.admin.mapper.AdminDanmakuMapper;
import com.kilikili.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminDanmakuService {
    private static final Logger log = LoggerFactory.getLogger(AdminDanmakuService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminDanmakuMapper adminDanmakuMapper;

    public AdminDanmakuService(AdminDanmakuMapper adminDanmakuMapper) {
        this.adminDanmakuMapper = adminDanmakuMapper;
    }

    public AdminDanmakuPage list(int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        long total = adminDanmakuMapper.count();
        List<AdminDanmakuItem> items = offset >= total
                ? List.of()
                : adminDanmakuMapper.list(offset, normalizedPageSize);
        boolean hasMore = offset + items.size() < total;
        return new AdminDanmakuPage(items, total, normalizedPage, normalizedPageSize, hasMore);
    }

    @Transactional
    public void delete(long id) {
        if (adminDanmakuMapper.deleteById(id) == 0) {
            throw new BusinessException("弹幕不存在或已被删除");
        }
        log.info("管理员删除弹幕: danmakuId={}", id);
    }
}
