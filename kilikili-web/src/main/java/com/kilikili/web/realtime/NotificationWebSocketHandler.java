package com.kilikili.web.realtime;

import com.kilikili.common.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时通知 WebSocket：客户端通过 /ws/notifications?token=xxx 建立连接，
 * 同一用户可同时保持多个连接（多标签页）。
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final String USER_ID_ATTRIBUTE = "realtimeUserId";

    private final AuthService authService;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session.getUri());
        if (userId == null) {
            log.warn("WebSocket 连接被拒绝: 无效或缺失的 token");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("登录已过期，请重新登录"));
            return;
        }
        session.getAttributes().put(USER_ID_ATTRIBUTE, userId);
        sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 连接已建立: userId={}, connections={}", userId, sessions.get(userId).size());
        session.sendMessage(new TextMessage("{\"kind\":\"CONNECTED\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 客户端心跳：收到 ping 回 pong，维持连接并快速发现断线
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get(USER_ID_ATTRIBUTE);
        if (userId instanceof Long id) {
            Set<WebSocketSession> userSessions = sessions.get(id);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) sessions.remove(id);
            }
            log.info("WebSocket 连接已关闭: userId={}, status={}", id, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket 传输异常: sessionId={}, error={}", session.getId(), exception.getMessage());
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    public void send(long userId, String payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return;
        for (WebSocketSession session : userSessions) {
            if (!session.isOpen()) {
                removeSession(userId, session);
                continue;
            }
            try {
                // WebSocketSession 非线程安全，多线程推送时需串行发送
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (Exception exception) {
                log.warn("WebSocket 消息推送失败: userId={}, sessionId={}, error={}", userId, session.getId(), exception.getMessage());
                removeSession(userId, session);
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignored) {
                    // 连接已不可用，忽略关闭异常
                }
            }
        }
    }

    private void removeSession(long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.remove(session);
        if (userSessions.isEmpty()) sessions.remove(userId);
    }

    private Long resolveUserId(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String pair : uri.getQuery().split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            if (!"token".equals(pair.substring(0, index))) continue;
            String token = pair.substring(index + 1);
            if (token.isBlank()) return null;
            try {
                return authService.requireUserId("Bearer " + token);
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }
}
