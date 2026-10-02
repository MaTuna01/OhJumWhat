package com.ohjumwhat.chat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** 채팅 허브 테스트용 연결: 핸드셰이크가 넣는 속성을 갖고, 받은 메시지와 닫힌 상태를 기록한다. */
class FakeWebSocketSession implements WebSocketSession {

	private final String id = UUID.randomUUID().toString();

	private final Map<String, Object> attributes = new HashMap<>();

	final List<WebSocketMessage<?>> sent = new ArrayList<>();

	CloseStatus closeStatus;

	FakeWebSocketSession(Long pollId, Long organizationId, Long userId, String httpSessionId) {
		attributes.put(ChatHub.POLL_ID, pollId);
		attributes.put(ChatHub.ORGANIZATION_ID, organizationId);
		attributes.put(ChatHub.USER_ID, userId);
		attributes.put(ChatHub.HTTP_SESSION_ID, httpSessionId);
	}

	/** 받은 글(JSON)만 */
	List<String> texts() {
		return sent.stream().filter(m -> m instanceof TextMessage).map(m -> ((TextMessage) m).getPayload()).toList();
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public URI getUri() {
		return null;
	}

	@Override
	public HttpHeaders getHandshakeHeaders() {
		return new HttpHeaders();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Principal getPrincipal() {
		return null;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return null;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return null;
	}

	@Override
	public String getAcceptedProtocol() {
		return null;
	}

	@Override
	public void setTextMessageSizeLimit(int messageSizeLimit) {
	}

	@Override
	public int getTextMessageSizeLimit() {
		return 0;
	}

	@Override
	public void setBinaryMessageSizeLimit(int messageSizeLimit) {
	}

	@Override
	public int getBinaryMessageSizeLimit() {
		return 0;
	}

	@Override
	public List<WebSocketExtension> getExtensions() {
		return List.of();
	}

	@Override
	public void sendMessage(WebSocketMessage<?> message) {
		sent.add(message);
	}

	@Override
	public boolean isOpen() {
		return closeStatus == null;
	}

	@Override
	public void close() {
		close(CloseStatus.NORMAL);
	}

	@Override
	public void close(CloseStatus status) {
		closeStatus = status;
	}
}
