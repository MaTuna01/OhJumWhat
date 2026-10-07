package com.ohjumwhat.chat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.stereotype.Component;

/**
 * 모니터링 지표: 지금 열린 투표 채팅(WebSocket) 연결 수. 점심시간에 채팅을 보고 있는 화면 수를 본다.
 * 관리 포트의 /actuator/prometheus에 ohjumwhat_chat_connections로 나간다(docs/DEPLOY.md 「모니터링」).
 */
@Component
class ChatMetrics implements MeterBinder {

	private final ChatHub chatHub;

	ChatMetrics(ChatHub chatHub) {
		this.chatHub = chatHub;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("ohjumwhat.chat.connections", chatHub, ChatHub::connectionCount)
			.description("열린 투표 채팅 WebSocket 연결 수")
			.register(registry);
	}
}
