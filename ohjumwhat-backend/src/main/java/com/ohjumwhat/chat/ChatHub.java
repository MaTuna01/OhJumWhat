package com.ohjumwhat.chat;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.databind.json.JsonMapper;

import com.ohjumwhat.organization.MembershipEndedEvent;
import com.ohjumwhat.organization.OrganizationDeletedEvent;
import com.ohjumwhat.poll.PollDeletedEvent;

/**
 * 투표 채팅의 WebSocket 연결을 서버 메모리에 들고 있다(앱이 하나라 외부 브로커가 필요 없다).
 * 받기 전용이라 서버가 보내기만 한다. 느린 연결이 다른 연결을 막지 않게 보내기 시간·버퍼를 제한한다.
 */
@Slf4j
@Component
public class ChatHub {

	/** 한 사람이 한 투표에 열 수 있는 연결(탭) 수 */
	static final int MAX_CONNECTIONS_PER_USER = 3;

	/** 채팅이 닫혀 끊는다. 화면은 다시 연결하지 않는다. */
	static final CloseStatus CHAT_CLOSED = new CloseStatus(4001, "chat closed");

	/** 더 볼 수 없어 끊는다(투표 삭제·조직 탈퇴·로그아웃·연결 수 초과). 화면은 다시 연결하지 않는다. */
	static final CloseStatus NOT_ALLOWED = new CloseStatus(4003, "not allowed");

	static final String POLL_ID = "pollId";

	static final String ORGANIZATION_ID = "organizationId";

	static final String USER_ID = "userId";

	static final String HTTP_SESSION_ID = "httpSessionId";

	private static final int SEND_TIME_LIMIT_MS = 5_000;

	private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

	/**
	 * 연결 확인 신호. 브라우저 JS는 WebSocket ping 프레임을 볼 수 없어서 글로 보낸다.
	 * 화면은 이것도 오지 않으면(반쯤 끊긴 연결: 네트워크 변경·프록시) 끊긴 것으로 보고 다시 연결한다.
	 */
	static final TextMessage HEARTBEAT = new TextMessage("{\"type\":\"ping\"}");

	private record Connection(WebSocketSession session, Long pollId, Long organizationId, Long userId,
			String httpSessionId) {
	}

	private final Map<String, Connection> connections = new ConcurrentHashMap<>();

	private final JsonMapper jsonMapper;

	public ChatHub(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** 핸드셰이크 때 넣은 속성으로 연결을 등록한다. 그사이 연결 수가 넘쳤으면 바로 끊는다. */
	void register(WebSocketSession session) {
		Map<String, Object> attributes = session.getAttributes();
		Long pollId = (Long) attributes.get(POLL_ID);
		Long userId = (Long) attributes.get(USER_ID);
		if (count(pollId, userId) >= MAX_CONNECTIONS_PER_USER) {
			closeQuietly(session, NOT_ALLOWED);
			return;
		}
		WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
				BUFFER_SIZE_LIMIT);
		connections.put(session.getId(), new Connection(concurrent, pollId, (Long) attributes.get(ORGANIZATION_ID),
				userId, (String) attributes.get(HTTP_SESSION_ID)));
		log.debug("채팅 연결: pollId={}, userId={}, 연결 수={}", pollId, userId, connections.size());
	}

	void unregister(WebSocketSession session) {
		connections.remove(session.getId());
	}

	/** 그 사람이 그 투표에 열어 둔 연결 수 */
	int count(Long pollId, Long userId) {
		return (int) connections.values().stream()
			.filter(c -> c.pollId().equals(pollId) && c.userId().equals(userId))
			.count();
	}

	/** 연결이 있는 투표들(주기적 점검용) */
	Set<Long> connectedPollIds() {
		return connections.values().stream().map(Connection::pollId).collect(Collectors.toSet());
	}

	/** 연결이 있는 투표의 조직 */
	Map<Long, Set<Long>> connectedUsersByOrganization() {
		return connections.values().stream()
			.collect(Collectors.groupingBy(Connection::organizationId,
					Collectors.mapping(Connection::userId, Collectors.toSet())));
	}

	/** 메시지를 쓰고 고치고 지운 트랜잭션이 커밋된 뒤에 같은 투표를 보는 연결로 보낸다. */
	@TransactionalEventListener
	void onChat(ChatEvent event) {
		TextMessage message = new TextMessage(jsonMapper.writeValueAsString(event.push()));
		matching(c -> c.pollId().equals(event.pollId())).forEach(c -> send(c, message));
	}

	@TransactionalEventListener
	void onPollDeleted(PollDeletedEvent event) {
		closeAll(c -> c.pollId().equals(event.pollId()), NOT_ALLOWED);
	}

	@TransactionalEventListener
	void onMembershipEnded(MembershipEndedEvent event) {
		closeAll(c -> c.organizationId().equals(event.organizationId()) && c.userId().equals(event.userId()),
				NOT_ALLOWED);
	}

	@TransactionalEventListener
	void onOrganizationDeleted(OrganizationDeletedEvent event) {
		closeAll(c -> c.organizationId().equals(event.organizationId()), NOT_ALLOWED);
	}

	/** 로그아웃: 그 로그인(HTTP 세션)으로 연 연결만 끊는다(다른 기기는 그대로). */
	public void closeHttpSession(String httpSessionId) {
		closeAll(c -> httpSessionId.equals(c.httpSessionId()), NOT_ALLOWED);
	}

	void closePoll(Long pollId, CloseStatus status) {
		closeAll(c -> c.pollId().equals(pollId), status);
	}

	/** 조직에서 더는 멤버가 아닌 사람의 연결을 끊는다(이벤트를 놓친 경우의 안전망). */
	void closeNonMembers(Long organizationId, Collection<Long> memberIds) {
		closeAll(c -> c.organizationId().equals(organizationId) && !memberIds.contains(c.userId()), NOT_ALLOWED);
	}

	/** 연결이 살아 있게(중간 프록시·NAT가 끊지 않게) 주기적으로 확인 신호를 보낸다. */
	void ping() {
		connections.values().forEach(c -> send(c, HEARTBEAT));
	}

	private Collection<Connection> matching(Predicate<Connection> filter) {
		return connections.values().stream().filter(filter).toList();
	}

	private void closeAll(Predicate<Connection> filter, CloseStatus status) {
		for (Connection c : matching(filter)) {
			connections.remove(c.session().getId());
			closeQuietly(c.session(), status);
		}
	}

	private void send(Connection c, WebSocketMessage<?> message) {
		try {
			c.session().sendMessage(message);
		}
		catch (IOException | RuntimeException e) {
			// 끊긴 연결이나 너무 느린 연결(보내기 시간·버퍼 초과)은 정리한다. 화면은 다시 연결해 목록을 다시 받는다.
			log.debug("채팅 보내기 실패로 연결 정리: pollId={}, userId={}, 오류={}", c.pollId(), c.userId(),
					e.getClass().getSimpleName());
			connections.remove(c.session().getId());
			closeQuietly(c.session(), CloseStatus.SESSION_NOT_RELIABLE);
		}
	}

	private static void closeQuietly(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		}
		catch (IOException | RuntimeException e) {
			// 이미 끊긴 연결
		}
	}
}
