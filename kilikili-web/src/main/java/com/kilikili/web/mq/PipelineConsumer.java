package com.kilikili.web.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 流水线消费者 + 死信消费者。
 *
 * <p>幂等：msgId 成功消费后写入 mq_consume_log；重复投递直接 ACK 跳过。
 * 失败返回 RECONSUME_LATER 由 RocketMQ 按退避级别重投，超过最大次数进入 %DLQ% 主题。</p>
 * <p>死信：%DLQ% 消费者把消息落库到 mq_dead_letter，供人工排查与回放。</p>
 */
@Component
public class PipelineConsumer {
    private static final Logger log = LoggerFactory.getLogger(PipelineConsumer.class);

    private final MqProperties properties;
    private final PipelineStageExecutor executor;
    private final MqConsumeLogMapper consumeLogMapper;
    private final MqDeadLetterMapper deadLetterMapper;
    private final VideoPipelineMapper pipelineMapper;
    private final ObjectMapper objectMapper;

    private DefaultMQPushConsumer consumer;
    private DefaultMQPushConsumer dlqConsumer;

    public PipelineConsumer(MqProperties properties, PipelineStageExecutor executor,
                            MqConsumeLogMapper consumeLogMapper, MqDeadLetterMapper deadLetterMapper,
                            VideoPipelineMapper pipelineMapper, ObjectMapper objectMapper) {
        this.properties = properties;
        this.executor = executor;
        this.consumeLogMapper = consumeLogMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.pipelineMapper = pipelineMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (!properties.enabled()) return;
        try {
            consumer = buildConsumer(properties.consumerGroup(), properties.topic());
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> consume(messages));
            consumer.start();
            log.info("流水线消费者已启动: group={}, topic={}, maxReconsume={}",
                    properties.consumerGroup(), properties.topic(), properties.maxReconsumeTimes());

            String dlqTopic = "%DLQ%" + properties.consumerGroup();
            dlqConsumer = buildConsumer(properties.consumerGroup() + "-dlq", dlqTopic);
            dlqConsumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> consumeDead(messages));
            dlqConsumer.start();
            log.info("死信消费者已启动: group={}-dlq, topic={}", properties.consumerGroup(), dlqTopic);
        } catch (Exception exception) {
            log.error("RocketMQ 消费者启动失败: {}", exception.getMessage());
        }
    }

    private DefaultMQPushConsumer buildConsumer(String group, String topic) throws Exception {
        DefaultMQPushConsumer pushConsumer = new DefaultMQPushConsumer(group);
        pushConsumer.setNamesrvAddr(properties.namesrvAddr());
        pushConsumer.setConsumeThreadMin(properties.consumeThreadMin());
        pushConsumer.setConsumeThreadMax(properties.consumeThreadMax());
        pushConsumer.setConsumeMessageBatchMaxSize(1);
        pushConsumer.setMaxReconsumeTimes(properties.maxReconsumeTimes());
        pushConsumer.subscribe(topic, "*");
        return pushConsumer;
    }

    private ConsumeConcurrentlyStatus consume(List<MessageExt> messages) {
        for (MessageExt message : messages) {
            PipelineMessage payload;
            try {
                payload = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8),
                        PipelineMessage.class);
            } catch (Exception exception) {
                log.error("流水线消息体解析失败，转入死信: msgId={}", message.getMsgId());
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            if (consumeLogMapper.exists(message.getMsgId()) > 0) {
                log.info("重复消息已跳过: msgId={}, videoId={}, stage={}",
                        message.getMsgId(), payload.videoId(), payload.stage());
                continue;
            }
            try {
                executor.execute(PipelineStage.valueOf(payload.stage()), payload.videoId());
                consumeLogMapper.markConsumed(message.getMsgId(), properties.topic(), payload.stage(), payload.videoId());
            } catch (Exception exception) {
                log.warn("流水线消费失败，等待重投: msgId={}, videoId={}, stage={}, reconsume={}, error={}",
                        message.getMsgId(), payload.videoId(), payload.stage(),
                        message.getReconsumeTimes(), exception.getMessage());
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private ConsumeConcurrentlyStatus consumeDead(List<MessageExt> messages) {
        for (MessageExt message : messages) {
            PipelineMessage payload = null;
            try {
                payload = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8),
                        PipelineMessage.class);
            } catch (Exception ignored) {
            }
            String stage = payload == null ? null : payload.stage();
            long videoId = payload == null ? 0 : payload.videoId();
            String reason = stage == null ? "消息体无法解析" : pipelineMapper.findError(videoId, stage);
            if (reason == null || reason.isBlank()) reason = "消费失败并超过最大重试次数";
            deadLetterMapper.insert(message.getMsgId(), message.getTopic(), stage, videoId,
                    new String(message.getBody(), StandardCharsets.UTF_8), reason, message.getReconsumeTimes());
            log.error("消息进入死信: msgId={}, videoId={}, stage={}, reason={}",
                    message.getMsgId(), videoId, stage, reason);
        }
        // 已落库，ACK 掉 RocketMQ 死信主题中的消息，后续从后台表回放
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) consumer.shutdown();
        if (dlqConsumer != null) dlqConsumer.shutdown();
    }

    public boolean running() {
        return consumer != null;
    }
}
