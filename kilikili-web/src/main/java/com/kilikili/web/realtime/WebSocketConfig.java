package com.kilikili.web.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final LiveWebSocketHandler liveWebSocketHandler;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler,
                           LiveWebSocketHandler liveWebSocketHandler) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.liveWebSocketHandler = liveWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
        registry.addHandler(liveWebSocketHandler, "/ws/live")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
