package com.kilikili.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 32, message = "分类名称不能超过32个字符")
        String name,

        @NotBlank(message = "分类编码不能为空")
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,63}$", message = "分类编码只能包含小写字母、数字和连字符")
        String code,

        Long parentId,
        Integer sortOrder
) {
}
