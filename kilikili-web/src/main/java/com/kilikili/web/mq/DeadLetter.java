package com.kilikili.web.mq;

import java.time.LocalDateTime;

/** 死信记录：超过最大重试次数的消息在此留存，可人工回放 */
public record DeadLetter(Long id, String msgId, String topic, String stage, Long videoId,
                         String payload, String error, Integer reconsumeTimes, String status,
                         LocalDateTime createdAt, LocalDateTime replayedAt) {
}
