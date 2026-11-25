package com.moava.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * F34 모임별 실시간 채팅 (WebSocket + STOMP). API 명세서 7.3절 [초안] 주소.
 * <pre>
 * 연결        /ws                              (STOMP CONNECT 헤더 Authorization: Bearer {JWT})
 * 보내기      SEND      /app/groups/{groupId}/chat     {"body":"..."}
 * 받기        SUBSCRIBE /topic/groups/{groupId}/chat   → ChatMessageResponse
 * 내 오류     SUBSCRIBE /user/queue/errors             → {"code","message"}
 * </pre>
 * 로그인·회원 확인은 ChatChannelInterceptor 가 한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatChannelInterceptor interceptor;

    public ChatWebSocketConfig(ChatChannelInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 기존 HTTP CORS 설정(SecurityConfig, 모든 출처 허용)과 맞춘다. 로그인 확인은 토큰으로 한다.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }
}
