package com.kilikili.web.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilikili.web.mapper.LiveRoomMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直播间 WebSocket：客户端通过 /ws/live?roomId=1 订阅当前房间，
 * 服务端广播弹幕、在线人数以及开播/下播事件。
 */
@Component
public class LiveWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LiveWebSocketHandler.class);
    private static final String ROOM_ATTRIBUTE = "liveRoomId";

    private final LiveRoomMapper liveRoomMapper;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public LiveWebSocketHandler(LiveRoomMapper liveRoomMapper, ObjectMapper objectMapper) {
        this.liveRoomMapper = liveRoomMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = resolveRoomId(session.getUri());
        if (roomId == null || liveRoomMapper.findById(roomId) == null) {
            log.warn("直播 WebSocket 连接被拒绝: roomId={}", roomId);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("直播间不存在"));
            return;
        }
        session.getAttributes().put(ROOM_ATTRIBUTE, roomId);
        roomSessions.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("直播 WebSocket 已连接: roomId={}, viewers={}", roomId, viewerCount(roomId));
        Map<String, Object> connected = new HashMap<>();
        connected.put("kind", "CONNECTED");
        connected.put("roomId", roomId);
        connected.put("viewerCount", viewerCount(roomId));
        send(session, connected);
        broadcastViewerCount(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 心跳：客户端定时发送 ping，服务端回 pong 以维持连接并快速发现断线
        if ("ping".equals(message.getPayload())) {
            synchronized (session) {
                session.sendMessage(new TextMessage("pong"));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object roomId = session.getAttributes().get(ROOM_ATTRIBUTE);
        if (roomId instanceof Long id) {
            removeSession(id, session);
            log.info("直播 WebSocket 已关闭: roomId={}, status={}, viewers={}", id, status, viewerCount(id));
            broadcastViewerCount(id);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("直播 WebSocket 传输异常: sessionId={}, error={}", session.getId(), exception.getMessage());
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    /** 当前房间在线连接数（同一用户多标签页会分别计数，用于展示热度） */
    public int viewerCount(long roomId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return 0;
        sessions.removeIf(session -> !session.isOpen());
        return sessions.size();
    }

    /** 向房间内所有连接广播事件 */
    public void broadcast(long roomId, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("直播事件序列化失败: roomId={}, payload={}", roomId, payload, exception);
            return;
        }
        for (WebSocketSession session : sessions) {
            send(session, json);
        }
    }

    private void broadcastViewerCount(long roomId) {
        int count = viewerCount(roomId);
        try {
            liveRoomMapper.updateViewerCount(roomId, count);
        } catch (RuntimeException exception) {
            log.warn("更新直播间在线人数失败: roomId={}, error={}", roomId, exception.getMessage());
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "LIVE_VIEWER");
        payload.put("roomId", roomId);
        payload.put("viewerCount", count);
        broadcast(roomId, payload);
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            send(session, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            log.warn("直播事件序列化失败: sessionId={}", session.getId(), exception);
        }
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) return;
        try {
            // WebSocketSession 非线程安全，多线程推送时需串行发送
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception exception) {
            log.warn("直播 WebSocket 推送失败: sessionId={}, error={}", session.getId(), exception.getMessage());
            Object roomId = session.getAttributes().get(ROOM_ATTRIBUTE);
            if (roomId instanceof Long id) removeSession(id, session);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception ignored) {
                // 连接已不可用，忽略关闭异常
            }
        }
    }

    private void removeSession(long roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        sessions.remove(session);
        if (sessions.isEmpty()) roomSessions.remove(roomId);
    }

    private Long resolveRoomId(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String pair : uri.getQuery().split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            if (!"roomId".equals(pair.substring(0, index))) continue;
            try {
                return Long.parseLong(pair.substring(index + 1));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }
}
