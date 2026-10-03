package com.ohjumwhat.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 투표 채팅 받기: /api/polls/{pollId}/ws. /api/** 아래라 로그인 확인(세션 쿠키, 아니면 401)을 그대로 받는다.
 * 허용 출처는 기본값(같은 출처만)이다. 프록시 뒤에서는 X-Forwarded-*로 원래 주소를 보고 비교한다.
 */
@Configuration
@EnableWebSocket
class ChatSocketConfig implements WebSocketConfigurer {

	private final ChatSocketHandler handler;

	private final ChatHandshakeInterceptor interceptor;

	ChatSocketConfig(ChatSocketHandler handler, ChatHandshakeInterceptor interceptor) {
		this.handler = handler;
		this.interceptor = interceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/api/polls/*/ws").addInterceptors(interceptor);
	}
}
