package com.kilikili.web.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** RocketMQ 生产者：流水线阶段消息统一从这里发出，tag 等于阶段名便于按阶段订阅 */
@Component
public class VideoPipelineProducer {
    private static final Logger log = LoggerFactory.getLogger(VideoPipelineProducer.class);

    private final MqProperties properties;
    private final ObjectMapper objectMapper;
    private DefaultMQProducer producer;
    private volatile boolean ready;

    public VideoPipelineProducer(MqProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!properties.enabled()) {
            log.info("RocketMQ 已关闭（kilikili.mq.enabled=false），流水线将同步执行");
            return;
        }
        try {
            producer = new DefaultMQProducer(properties.producerGroup());
            producer.setNamesrvAddr(properties.namesrvAddr());
            producer.setSendMsgTimeout(properties.sendTimeoutMs());
            producer.setRetryTimesWhenSendFailed(2);
            producer.start();
            ready = true;
            log.info("RocketMQ 生产者已启动: namesrv={}, group={}, topic={}",
                    properties.namesrvAddr(), properties.producerGroup(), properties.topic());
        } catch (Exception exception) {
            ready = false;
            log.error("RocketMQ 生产者启动失败，投稿流水线改由补偿任务兜底: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) producer.shutdown();
    }

    public boolean ready() {
        return ready;
    }

    public SendResult send(PipelineMessage message) throws Exception {
        if (!ready) throw new IllegalStateException("RocketMQ 生产者未就绪");
        Message mqMessage = new Message(properties.topic(), message.stage(),
                String.valueOf(message.videoId()),
                objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));
        SendResult result = producer.send(mqMessage);
        log.info("流水线消息已投递: videoId={}, stage={}, msgId={}, queue={}",
                message.videoId(), message.stage(), result.getMsgId(), result.getMessageQueue().getQueueId());
        return result;
    }
}
