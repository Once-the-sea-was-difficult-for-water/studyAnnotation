package com.multiagent.api.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP/SockJS 配置。
 * <p>
 * 端点: /ws (SockJS fallback)
 * 消息代理前缀: /topic (广播), /queue (点对点)
 * 应用目的地前缀: /app
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.stomp.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单内存消息代理，客户端订阅 /topic/** 或 /queue/**
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息的目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 点对点消息前缀（用于向特定用户推送）
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .withSockJS();
    }
}
