package com.kilikili.web.entity;

/** 弹幕热度图分桶：bucket 为时间桶序号，count 为该桶内弹幕条数 */
public record DanmakuHeatBucket(Long bucket, Long count) {
}
