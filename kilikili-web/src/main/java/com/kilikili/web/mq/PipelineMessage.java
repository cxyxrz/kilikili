package com.kilikili.web.mq;

/** 流水线消息体：只携带视频与阶段，业务数据由消费者回查数据库，避免消息体与库不一致 */
public record PipelineMessage(String eventId, long videoId, String stage, long createdAt) {
}
