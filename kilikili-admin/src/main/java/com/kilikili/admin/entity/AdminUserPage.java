package com.kilikili.admin.entity;

import java.util.List;

public record AdminUserPage(
        List<AdminUserItem> items,
        long total,
        int page,
        int pageSize,
        boolean hasMore
) {
}
