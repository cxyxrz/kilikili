package com.kilikili.web.entity;

/** 弹幕敏感词：action=BLOCK 直接拦截，REVIEW 放行并记录复核日志 */
public record SensitiveWord(Long id, String word, String action, Boolean enabled) {
}
