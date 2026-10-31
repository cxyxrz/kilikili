package com.kilikili.web.mq;

/** 投稿流水线阶段：一条消息只负责一个阶段，消费成功后再投递下一阶段 */
public enum PipelineStage {
    REVIEW,
    TRANSCODE,
    NOTIFY,
    INDEX;

    public PipelineStage next() {
        return switch (this) {
            case REVIEW -> TRANSCODE;
            case TRANSCODE -> NOTIFY;
            case NOTIFY -> INDEX;
            case INDEX -> null;
        };
    }
}
