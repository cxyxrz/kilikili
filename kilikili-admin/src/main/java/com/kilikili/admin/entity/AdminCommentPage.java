package com.kilikili.admin.entity;

import java.util.List;

public record AdminCommentPage(
        List<AdminCommentItem> items,
        long total,
        int page,
        int pageSize,
        boolean hasMore
) {
}
