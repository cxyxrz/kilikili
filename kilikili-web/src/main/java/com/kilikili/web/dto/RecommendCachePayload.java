package com.kilikili.web.dto;

import com.kilikili.web.entity.RecommendSource;

import java.util.List;

/**
 * 推荐页缓存载荷：条目与总数一起缓存，避免每次请求都回源 count，
 * 同时用空 items 作为「空值缓存」抵御缓存穿透。
 */
public record RecommendCachePayload(List<RecommendSource> items, long total) {
}
