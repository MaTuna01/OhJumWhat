package com.ohjumwhat.chat;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollService;

/**
 * 채팅 연결(/api/polls/{pollId}/ws) 전에 확인한다. 로그인은 /api/**의 보안 설정이 이미 확인했다(아니면 401).
 * 멤버가 아니면 404, 채팅이 닫혔으면 409, 한 사람이 한 투표에 연결을 3개 넘게 열면 429로 거절한다.
 */
@Component
class ChatHandshakeInterceptor implements HandshakeInterceptor {

	private static final Pattern PATH = Pattern.compile("^/api/polls/(\\d{1,18})/ws$");

	private final PollService pollService;

	private final ChatHub chatHub;

	private final Clock clock;

	ChatHandshakeInterceptor(PollService pollService, ChatHub chatHub, Clock clock) {
		this.pollService = pollService;
		this.chatHub = chatHub;
		this.clock = clock;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
			Map<String, Object> attributes) {
		Matcher path = PATH.matcher(request.getURI().getPath());
		if (!path.matches()) {
			return reject(response, HttpStatus.NOT_FOUND);
		}
		LoginUser loginUser = loginUser(request.getPrincipal());
		if (loginUser == null) {
			return reject(response, HttpStatus.UNAUTHORIZED);
		}
		Long pollId = Long.valueOf(path.group(1));
		Long userId = loginUser.getUserId();
		Poll poll;
		try {
			poll = pollService.getForMember(pollId, userId);
		}
		catch (ApiException e) {
			return reject(response, e.getStatus());
		}
		if (!poll.isChatOpen(Instant.now(clock))) {
			return reject(response, HttpStatus.CONFLICT);
		}
		if (chatHub.count(pollId, userId) >= ChatHub.MAX_CONNECTIONS_PER_USER) {
			return reject(response, HttpStatus.TOO_MANY_REQUESTS);
		}
		attributes.put(ChatHub.POLL_ID, pollId);
		attributes.put(ChatHub.ORGANIZATION_ID, poll.getOrganizationId());
		attributes.put(ChatHub.USER_ID, userId);
		attributes.put(ChatHub.HTTP_SESSION_ID, httpSessionId(request));
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
			Exception exception) {
	}

	private static boolean reject(ServerHttpResponse response, HttpStatus status) {
		response.setStatusCode(status);
		return false;
	}

	private static LoginUser loginUser(Principal principal) {
		return principal instanceof Authentication authentication
				&& authentication.getPrincipal() instanceof LoginUser loginUser ? loginUser : null;
	}

	/** 로그아웃 때 이 로그인으로 연 연결만 끊으려고 세션 ID를 기억한다. */
	private static String httpSessionId(ServerHttpRequest request) {
		if (request instanceof ServletServerHttpRequest servlet) {
			HttpSession session = servlet.getServletRequest().getSession(false);
			return session == null ? null : session.getId();
		}
		return null;
	}
}
