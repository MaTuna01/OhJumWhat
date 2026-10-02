package com.ohjumwhat.chat;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 채팅 받기 전용 연결. 보내기·고치기·지우기는 REST로 하므로 화면이 보내는 글은 무시한다. */
@Component
class ChatSocketHandler extends TextWebSocketHandler {

	private final ChatHub chatHub;

	ChatSocketHandler(ChatHub chatHub) {
		this.chatHub = chatHub;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		chatHub.register(session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		// 받기 전용
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		chatHub.unregister(session);
	}
}
