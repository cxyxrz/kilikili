package com.kilikili.web.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 统一推送入口：把业务事件序列化为 JSON 后交给 WebSocket 连接池。
 * 用户不在线时直接丢弃，前端会在下次拉取时补齐数据。
 */
@Service
public class RealtimePushService {
    private static final Logger log = LoggerFactory.getLogger(RealtimePushService.class);
    private final NotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public RealtimePushService(NotificationWebSocketHandler webSocketHandler, ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    public void push(long userId, Map<String, Object> payload) {
        try {
            webSocketHandler.send(userId, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            log.warn("实时消息序列化失败: userId={}, payload={}", userId, payload, exception);
        }
    }

    /** 事务提交后再推送，避免客户端收到消息时数据库里还查不到 */
    public void pushAfterCommit(long userId, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { push(userId, payload); }
            });
            return;
        }
        push(userId, payload);
    }
}
