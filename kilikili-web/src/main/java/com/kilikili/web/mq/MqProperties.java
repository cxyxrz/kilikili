package com.kilikili.web.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 相关配置。enabled=false 时退化为进程内同步执行流水线，
 * 便于本地未启动 broker 时仍能跑通业务。
 */
@ConfigurationProperties(prefix = "kilikili.mq")
public record MqProperties(
        boolean enabled,
        String namesrvAddr,
        String producerGroup,
        String consumerGroup,
        String topic,
        int maxReconsumeTimes,
        int sendTimeoutMs,
        int consumeThreadMin,
        int consumeThreadMax,
        long reconcileIntervalMs,
        int reconcileDelaySeconds) {

    public String namesrvAddr() {
        return blank(namesrvAddr) ? "127.0.0.1:9876" : namesrvAddr;
    }

    public String topic() {
        return blank(topic) ? "kilikili-video-pipeline" : topic;
    }

    public String producerGroup() {
        return blank(producerGroup) ? "kilikili-video-producer" : producerGroup;
    }

    public String consumerGroup() {
        return blank(consumerGroup) ? "kilikili-video-pipeline-consumer" : consumerGroup;
    }

    public int maxReconsumeTimes() {
        return maxReconsumeTimes <= 0 ? 3 : maxReconsumeTimes;
    }

    public int sendTimeoutMs() {
        return sendTimeoutMs <= 0 ? 3000 : sendTimeoutMs;
    }

    public int consumeThreadMin() {
        return consumeThreadMin <= 0 ? 2 : consumeThreadMin;
    }

    public int consumeThreadMax() {
        return Math.max(consumeThreadMin(), consumeThreadMax <= 0 ? 6 : consumeThreadMax);
    }

    public long reconcileIntervalMs() {
        return reconcileIntervalMs <= 0 ? 60_000 : reconcileIntervalMs;
    }

    /** 阶段停留多久才判定为“疑似丢失”，交由补偿任务重发 */
    public int reconcileDelaySeconds() {
        return reconcileDelaySeconds <= 0 ? 60 : reconcileDelaySeconds;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
