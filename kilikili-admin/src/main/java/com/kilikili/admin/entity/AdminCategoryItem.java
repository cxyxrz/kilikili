package com.kilikili.admin.entity;

public record AdminCategoryItem(
        Long id,
        String name,
        String code,
        Long parentId,
        Integer sortOrder,
        Long childCount,
        Long videoCount
) {
}
