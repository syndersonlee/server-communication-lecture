package com.yanolja.practice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * [실습 4] WebSocket + STOMP 설정
 *
 *  - 연결 엔드포인트:  /ws  (SockJS 폴백 포함)
 *  - 클라이언트 → 서버:  /app/...   (예: /app/chat.send)
 *  - 서버 → 구독자:     /topic/...  (예: /topic/room)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 내장 SimpleBroker: 단일 서버 메모리 기반 (운영 다중서버는 외부 브로커 relay 필요)
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
