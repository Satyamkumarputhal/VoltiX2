package com.voltix.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP-over-WebSocket endpoint for real-time alert broadcasting
 * to the VoltiX dashboard frontend.
 *
 * Endpoint: ws://localhost:8080/ws/alerts (SockJS fallback enabled)
 * Destination prefix: /topic (subscribe) and /app (send)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker; subscriptions use /topic/ prefix
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/alerts")
                .setAllowedOriginPatterns("*") // Frontend dev server at localhost:5173
                .withSockJS();                 // SockJS fallback for environments without native WS
    }
}
