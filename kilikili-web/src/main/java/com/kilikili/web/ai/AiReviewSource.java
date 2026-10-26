package com.kilikili.web.ai;

/**
 * 待 AI 审核的视频文本信息，只取审核判定需要的字段。
 */
public record AiReviewSource(
        long id,
        String title,
        String description,
        long categoryId,
        String creationDeclaration,
        String authorName) {
}
