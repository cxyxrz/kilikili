package com.kilikili.web.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 投稿流水线入口：投稿/审核通过后调用 start()，之后由消息驱动各阶段推进。
 * 先落 video_pipeline 状态再发消息，发送失败也不会丢阶段，补偿任务会重新投递。
 */
@Service
public class VideoPipelineService {
    private static final Logger log = LoggerFactory.getLogger(VideoPipelineService.class);

    private final VideoPipelineMapper pipelineMapper;
    private final VideoPipelineProducer producer;
    private final MqProperties properties;
    private final ObjectProvider<PipelineStageExecutor> executorProvider;

    public VideoPipelineService(VideoPipelineMapper pipelineMapper, VideoPipelineProducer producer,
                                MqProperties properties, ObjectProvider<PipelineStageExecutor> executorProvider) {
        this.pipelineMapper = pipelineMapper;
        this.producer = producer;
        this.properties = properties;
        this.executorProvider = executorProvider;
    }

    /** 从审核阶段开始推进整条流水线 */
    public void start(long videoId) {
        publish(PipelineStage.REVIEW, videoId);
    }

    public boolean publish(PipelineStage stage, long videoId) {
        String eventId = UUID.randomUUID().toString();
        pipelineMapper.ensure(videoId, stage.name(), eventId);
        PipelineMessage message = new PipelineMessage(eventId, videoId, stage.name(), System.currentTimeMillis());
        if (!properties.enabled()) {
            // 未启用 MQ：退化为同步执行，保证功能可用
            return executorProvider.getObject().execute(stage, videoId);
        }
        try {
            producer.send(message);
            return true;
        } catch (Exception exception) {
            // 发送失败时不抛给业务，状态保持 PENDING，由补偿任务重投
            log.error("流水线消息投递失败，等待补偿重投: videoId={}, stage={}, error={}",
                    videoId, stage, exception.getMessage());
            return false;
        }
    }
}
