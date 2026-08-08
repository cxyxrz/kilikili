package com.kilikili.admin.entity;

import java.util.List;

public record AdminDanmakuPage(
        List<AdminDanmakuItem> items,
        long total,
        int page,
        int pageSize,
        boolean hasMore) {
}
