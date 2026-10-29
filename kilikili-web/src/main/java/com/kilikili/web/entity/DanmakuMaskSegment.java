package com.kilikili.web.entity;

/**
 * 弹幕防挡脸时间片段：startMs~endMs 期间，画面上 maskTop~maskBottom（0~1 归一化高度）为人脸区域，
 * 弹幕渲染时需避开这段区域。
 */
public record DanmakuMaskSegment(Long id, Long videoId, Integer startMs, Integer endMs,
                                 Double maskTop, Double maskBottom, Double faceRatio) {
}
