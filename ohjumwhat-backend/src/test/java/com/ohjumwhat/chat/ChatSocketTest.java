package com.ohjumwhat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.ohjumwhat.FakeKakaoLocalConfiguration;
import com.ohjumwhat.FakeNaverShortLinksConfiguration;
import com.ohjumwhat.TestClock;
import com.ohjumwhat.TestClockConfiguration;
import com.ohjumwhat.TestcontainersConfiguration;
import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 실제 서버 포트로 채팅 WebSocket(/api/polls/{pollId}/ws)에 연결해 본다.
 * 로그인(세션 쿠키)·멤버·출처·채팅 기간을 핸드셰이크에서 확인하고, 커밋된 메시지를 받는지 본다.
 * 운영처럼 X-Forwarded-*로 원래 주소를 보게 해서(Caddy 뒤) 같은 출처 검사도 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "server.forward-headers-strategy=framework")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeNaverShortLinksConfiguration.class,
		FakeKakaoLocalConfiguration.class })
class ChatSocketTest {

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	PollService pollService;

	@Autowired
	ChatService chatService;

	@Autowired
	ChatRateLimiter chatRateLimiter;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestClock clock;

	final StandardWebSocketClient client = new StandardWebSocketClient();

	final BlockingQueue<String> received = new LinkedBlockingQueue<>();

	User kim;

	User park;

	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		OrganizationResponse dev = organizationService.create(kim.getId(), "개발팀");
		pollId = pollService.create(dev.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@AfterEach
	void cleanDatabase() {
		clock.reset();
		chatRateLimiter.clear();
		jdbcTemplate.execute("""
				TRUNCATE users, organizations, memberships, poll_schedules, polls, menu_options, menu_comments, votes,
					chat_messages, spring_session, blocked_accounts, notices
				RESTART IDENTITY CASCADE""");
	}

	@Test
	void 멤버는_연결해서_커밋된_새_메시지를_받는다() throws Exception {
		WebSocketSession socket = connect(sessionCookie(kim), origin());

		chatService.send(pollId, kim.getId(), "김치찌개 어때요?");

		String json = received.poll(5, TimeUnit.SECONDS);
		assertThat(json).contains("\"type\":\"created\"").contains("김치찌개 어때요?");
		socket.close();
	}

	@Test
	void 로그인하지_않았거나_멤버가_아니면_연결할_수_없다() {
		assertThatThrownBy(() -> connect(null, origin())).isInstanceOf(ExecutionException.class);
		assertThatThrownBy(() -> connect(sessionCookie(park), origin())).isInstanceOf(ExecutionException.class);
	}

	@Test
	void 다른_출처의_페이지에서는_연결할_수_없다() {
		assertThatThrownBy(() -> connect(sessionCookie(kim), "https://evil.example"))
			.isInstanceOf(ExecutionException.class);
	}

	@Test
	void 프록시_뒤에서는_원래_주소를_기준으로_같은_출처인지_본다() throws Exception {
		WebSocketHttpHeaders proxied = new WebSocketHttpHeaders();
		proxied.add("X-Forwarded-Proto", "https");
		proxied.add("X-Forwarded-Host", "www.ohjumwhat.example");
		connectWith(proxied, sessionCookie(kim), "https://www.ohjumwhat.example").close();

		WebSocketHttpHeaders spoofed = new WebSocketHttpHeaders();
		spoofed.add("X-Forwarded-Proto", "https");
		spoofed.add("X-Forwarded-Host", "www.ohjumwhat.example");
		assertThatThrownBy(() -> connectWith(spoofed, sessionCookie(kim), "https://evil.example"))
			.isInstanceOf(ExecutionException.class);
	}

	@Test
	void 채팅이_닫힌_투표에는_연결할_수_없다() {
		clock.set(Instant.parse("2026-09-30T03:50:00Z")); // 마감 1시간 뒤
		assertThatThrownBy(() -> connect(sessionCookie(kim), origin())).isInstanceOf(ExecutionException.class);
	}

	private WebSocketSession connect(String cookie, String origin) throws Exception {
		return connectWith(new WebSocketHttpHeaders(), cookie, origin);
	}

	private WebSocketSession connectWith(WebSocketHttpHeaders headers, String cookie, String origin) throws Exception {
		if (cookie != null) {
			headers.add("Cookie", cookie);
		}
		headers.setOrigin(origin);
		TextWebSocketHandler handler = new TextWebSocketHandler() {
			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) {
				received.add(message.getPayload());
			}
		};
		URI uri = URI.create("ws://localhost:" + port + "/api/polls/" + pollId + "/ws");
		return client.execute(handler, headers, uri).get(5, TimeUnit.SECONDS);
	}

	private String origin() {
		return "http://localhost:" + port;
	}

	/** 그 회원으로 구글 로그인한 세션을 저장하고, 브라우저가 보낼 SESSION 쿠키를 만든다. */
	private String sessionCookie(User user) {
		OidcIdToken idToken = OidcIdToken.withTokenValue("test-token")
			.subject(user.getGoogleSub())
			.claim("email", user.getEmail())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.build();
		LoginUser loginUser = new LoginUser(user.getId(),
				new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken));
		OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(loginUser,
				loginUser.getAuthorities(), "google");
		String id = saveSession(sessionRepository, new SecurityContextImpl(authentication));
		return "SESSION=" + Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
	}

	private static <S extends Session> String saveSession(FindByIndexNameSessionRepository<S> repository,
			SecurityContextImpl context) {
		S session = repository.createSession();
		session.setAttribute("SPRING_SECURITY_CONTEXT", context);
		repository.save(session);
		return session.getId();
	}
}
