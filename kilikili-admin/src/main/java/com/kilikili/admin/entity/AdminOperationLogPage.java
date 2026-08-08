package com.kilikili.admin.entity;

import java.util.List;

public record AdminOperationLogPage(
        List<AdminOperationLogItem> items,
        long total,
        int page,
        int pageSize,
        boolean hasMore
) {
}
