package com.ohjumwhat.chat;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.PingMessage;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class ChatIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	MembershipRepository membershipRepository;

	@Autowired
	PollService pollService;

	@Autowired
	ChatService chatService;

	@Autowired
	ChatMessageRepository chatMessageRepository;

	@Autowired
	ChatHub chatHub;

	@Autowired
	ChatSweeper chatSweeper;

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	User admin;

	User kim;

	User lee;

	User park;

	Long orgId;

	/** 개발팀 점심, 11:50 마감 → 채팅은 12:50까지 */
	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0); // 수요일 오전 11:00 (한국 시간)
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		OrganizationResponse dev = organizationService.create(kim.getId(), "개발팀");
		orgId = dev.id();
		inviteService.join(dev.inviteToken(), lee.getId());
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@AfterEach
	void closeConnections() {
		chatHub.connectedPollIds().forEach(id -> chatHub.closePoll(id, ChatHub.NOT_ALLOWED));
	}

	@Test
	void 채팅은_마감_1시간_뒤까지_쓰고_그_뒤에는_읽기만_한다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + pollId).with(loginAs(kim)))
			.andExpect(jsonPath("$.chatClosesAt").value("2026-09-30T03:50:00Z"));
		send(kim, "  김치찌개 어때요?  ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("김치찌개 어때요?"))
			.andExpect(jsonPath("$.author.name").value("김철수"))
			.andExpect(jsonPath("$.createdAt").value("2026-09-30T02:00:00Z"))
			.andExpect(jsonPath("$.editedAt").value(nullValue()))
			.andExpect(jsonPath("$.deleted").value(false));

		clock.set(2026, 9, 30, 12, 49); // 마감 뒤에도 결과를 보고 만날 곳을 정한다.
		send(lee, "1층에서 만나요").andExpect(status().isCreated());

		clock.set(2026, 9, 30, 12, 50);
		send(lee, "늦었어요")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("채팅이 닫혔어요."));
		mockMvc.perform(get(messagesUrl()).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages", hasSize(2)))
			.andExpect(jsonPath("$.messages[1].body").value("1층에서 만나요"))
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void 지금_마감하면_채팅도_그때부터_1시간_뒤에_닫힌다() throws Exception {
		clock.set(2026, 9, 30, 11, 10);
		mockMvc.perform(post("/api/polls/" + pollId + "/close").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chatClosesAt").value("2026-09-30T03:10:00Z"));

		clock.set(2026, 9, 30, 12, 9);
		send(kim, "다들 어디예요?").andExpect(status().isCreated());
		clock.set(2026, 9, 30, 12, 10);
		send(kim, "아무도 없나요").andExpect(status().isConflict());
	}

	@Test
	void 멤버가_아니면_404이고_남의_메시지는_고치거나_지울_수_없다() throws Exception {
		mockMvc.perform(get(messagesUrl()).with(loginAs(park)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("투표를 찾을 수 없어요."));
		send(park, "안녕하세요").andExpect(status().isNotFound());

		Long messageId = chatService.send(pollId, kim.getId(), "김치찌개 어때요?").id();
		edit(lee, messageId, "제가 고칠게요")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("내가 쓴 메시지만 고칠 수 있어요."));
		remove(lee, messageId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("내가 쓴 메시지만 지울 수 있어요."));
	}

	@Test
	void 고치면_수정한_시각이_남고_지우면_본문을_비운_채_남는다() throws Exception {
		Long messageId = chatService.send(pollId, kim.getId(), "김치찌개 어때요?").id();

		clock.set(2026, 9, 30, 11, 5);
		edit(kim, messageId, "돈가스 어때요?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value("돈가스 어때요?"))
			.andExpect(jsonPath("$.editedAt").value("2026-09-30T02:05:00Z"))
			.andExpect(jsonPath("$.createdAt").value("2026-09-30T02:00:00Z"));
		remove(kim, messageId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value(nullValue()))
			.andExpect(jsonPath("$.deleted").value(true));

		edit(kim, messageId, "다시").andExpect(status().isNotFound());
		remove(kim, messageId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("메시지를 찾을 수 없어요."));
		mockMvc.perform(get(messagesUrl()).with(loginAs(lee)))
			.andExpect(jsonPath("$.messages[0].deleted").value(true))
			.andExpect(jsonPath("$.messages[0].body").value(nullValue()))
			.andExpect(jsonPath("$.messages[0].author.name").value("김철수"));
	}

	@Test
	void 한_사람이_10초에_10개까지_보낸다() throws Exception {
		for (int i = 1; i <= 10; i++) {
			send(kim, "메시지 " + i).andExpect(status().isCreated());
		}
		send(kim, "하나 더")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.message").value("메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요."));
		send(lee, "저는 보낼 수 있어요").andExpect(status().isCreated());

		clock.set(Instant.parse("2026-09-30T02:00:10Z"));
		send(kim, "이제 보내져요").andExpect(status().isCreated());
	}

	@Test
	void 메시지는_줄바꿈을_허용하고_300자까지_쓴다() throws Exception {
		send(kim, "첫 줄\\n둘째 줄").andExpect(status().isCreated()).andExpect(jsonPath("$.body").value("첫 줄\n둘째 줄"));
		send(kim, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));
		send(kim, "벨\\u0007").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("쓸 수 없는 문자가 있어요."));
		send(kim, "가".repeat(301))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("300자 이하로 입력해 주세요."));
		send(kim, "😋".repeat(300)).andExpect(status().isCreated());
	}

	@Test
	void 지난_메시지는_50개씩_받는다() throws Exception {
		Instant at = Instant.now(clock);
		for (int i = 1; i <= 55; i++) {
			chatMessageRepository.save(new ChatMessage(pollId, kim.getId(), "메시지 " + i, at));
		}
		String first = mockMvc.perform(get(messagesUrl()).with(loginAs(lee)))
			.andExpect(jsonPath("$.messages", hasSize(50)))
			.andExpect(jsonPath("$.messages[0].body").value("메시지 6"))
			.andExpect(jsonPath("$.messages[49].body").value("메시지 55"))
			.andExpect(jsonPath("$.hasMore").value(true))
			.andReturn().getResponse().getContentAsString();
		Number oldestId = JsonPath.read(first, "$.messages[0].id");

		mockMvc.perform(get(messagesUrl() + "?before=" + oldestId).with(loginAs(lee)))
			.andExpect(jsonPath("$.messages", hasSize(5)))
			.andExpect(jsonPath("$.messages[0].body").value("메시지 1"))
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void 강제_탈퇴한_회원의_메시지는_탈퇴한_사용자로_남는다() throws Exception {
		chatService.send(pollId, lee.getId(), "1층에서 만나요");
		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get(messagesUrl()).with(loginAs(kim)))
			.andExpect(jsonPath("$.messages[0].body").value("1층에서 만나요"))
			.andExpect(jsonPath("$.messages[0].author").value(nullValue()));
	}

	@Test
	void 관리자는_채팅이_닫힌_뒤에도_메시지를_보고_지운다() throws Exception {
		Long messageId = chatService.send(pollId, lee.getId(), "부적절한 글").id();
		clock.set(2026, 9, 30, 13, 0);

		mockMvc.perform(get("/api/admin/polls/" + pollId + "/messages").with(loginAs(kim)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/polls/" + pollId + "/messages").with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[0].body").value("부적절한 글"));
		mockMvc.perform(delete("/api/admin/chat-messages/" + messageId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deleted").value(true));
		mockMvc.perform(delete("/api/admin/chat-messages/" + messageId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 쓰고_고치고_지우면_같은_투표의_연결에만_보낸다() throws Exception {
		Long otherPollId = pollService.create(orgId, kim.getId(), new PollRequest("저녁", "18:00")).id();
		FakeWebSocketSession kimTab = connect(pollId, kim);
		FakeWebSocketSession leeTab = connect(pollId, lee);
		FakeWebSocketSession otherPollTab = connect(otherPollId, kim);

		String sent = send(kim, "김치찌개 어때요?").andReturn().getResponse().getContentAsString();
		Number messageId = JsonPath.read(sent, "$.id");
		edit(kim, messageId.longValue(), "돈가스 어때요?");
		remove(kim, messageId.longValue());

		assertThat(leeTab.texts()).hasSize(3);
		assertThat(kimTab.texts()).hasSize(3);
		assertThat(otherPollTab.texts()).isEmpty();
		String created = leeTab.texts().get(0);
		assertThat((String) JsonPath.read(created, "$.type")).isEqualTo("created");
		assertThat((String) JsonPath.read(created, "$.message.body")).isEqualTo("김치찌개 어때요?");
		assertThat((String) JsonPath.read(created, "$.message.author.name")).isEqualTo("김철수");
		assertThat((String) JsonPath.read(leeTab.texts().get(1), "$.type")).isEqualTo("updated");
		String deleted = leeTab.texts().get(2);
		assertThat((String) JsonPath.read(deleted, "$.type")).isEqualTo("deleted");
		assertThat((Object) JsonPath.read(deleted, "$.message.body")).isNull();
	}

	@Test
	void 실패한_쓰기는_보내지_않는다() throws Exception {
		FakeWebSocketSession leeTab = connect(pollId, lee);
		send(kim, "가".repeat(301)).andExpect(status().isBadRequest());
		assertThat(leeTab.texts()).isEmpty();
	}

	@Test
	void 조직을_떠나거나_투표가_지워지면_연결을_끊는다() throws Exception {
		FakeWebSocketSession kimTab = connect(pollId, kim);
		FakeWebSocketSession leeTab = connect(pollId, lee);

		mockMvc.perform(delete("/api/orgs/" + orgId + "/membership").with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isOk());
		assertThat(leeTab.closeStatus).isEqualTo(ChatHub.NOT_ALLOWED);
		assertThat(kimTab.isOpen()).isTrue();

		mockMvc.perform(delete("/api/polls/" + pollId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(kimTab.closeStatus).isEqualTo(ChatHub.NOT_ALLOWED);
		assertThat(chatHub.connectedPollIds()).isEmpty();
	}

	@Test
	void 로그아웃하면_그_로그인으로_연_연결만_끊는다() throws Exception {
		String phoneSessionId = createSession(sessionRepository);
		FakeWebSocketSession phone = connect(pollId, kim, phoneSessionId);
		FakeWebSocketSession laptop = connect(pollId, kim, "session-laptop");

		// 세션 쿠키는 세션 ID를 base64로 담는다(Spring Session 기본값). xsrf()는 쿠키를 통째로 바꾸므로 CSRF 쿠키도 직접 붙인다.
		Cookie session = new Cookie("SESSION",
				Base64.getEncoder().encodeToString(phoneSessionId.getBytes(StandardCharsets.UTF_8)));
		mockMvc.perform(post("/logout").cookie(session, new Cookie("XSRF-TOKEN", "token")).header("X-XSRF-TOKEN", "token")
				.with(loginAs(kim)))
			.andExpect(status().isNoContent());
		assertThat(phone.closeStatus).isEqualTo(ChatHub.NOT_ALLOWED);
		assertThat(laptop.isOpen()).isTrue();
	}

	@Test
	void 한_사람이_한_투표에_연결을_3개까지_연다() {
		connect(pollId, kim);
		connect(pollId, kim);
		connect(pollId, kim);
		FakeWebSocketSession fourth = connect(pollId, kim);

		assertThat(fourth.closeStatus).isEqualTo(ChatHub.NOT_ALLOWED);
		assertThat(chatHub.count(pollId, kim.getId())).isEqualTo(ChatHub.MAX_CONNECTIONS_PER_USER);
	}

	@Test
	void 점검은_멤버가_아닌_연결과_닫힌_채팅을_끊고_나머지에_ping을_보낸다() {
		FakeWebSocketSession kimTab = connect(pollId, kim);
		FakeWebSocketSession leeTab = connect(pollId, lee);
		// 이벤트 없이 멤버에서 빠진 경우(안전망)
		membershipRepository.delete(membershipRepository.findByOrganizationIdAndUserId(orgId, lee.getId()).orElseThrow());

		chatSweeper.sweep();
		assertThat(leeTab.closeStatus).isEqualTo(ChatHub.NOT_ALLOWED);
		assertThat(kimTab.sent).hasAtLeastOneElementOfType(PingMessage.class);

		clock.set(2026, 9, 30, 12, 50);
		chatSweeper.sweep();
		assertThat(kimTab.closeStatus).isEqualTo(ChatHub.CHAT_CLOSED);
	}

	@Test
	void WebSocket을_켜도_정기_투표_스케줄러를_가로채는_TaskScheduler_빈이_생기지_않는다() {
		// @Scheduled는 TaskScheduler 빈이 하나 있으면 그것을 쓴다. WebSocket 설정이 그런 빈을 만들면 정기 투표가 그 스케줄러로 돈다.
		assertThat(applicationContext.getBeansOfType(TaskScheduler.class)).isEmpty();
	}

	private static <S extends Session> String createSession(FindByIndexNameSessionRepository<S> repository) {
		S session = repository.createSession();
		repository.save(session);
		return session.getId();
	}

	private FakeWebSocketSession connect(Long poll, User user) {
		return connect(poll, user, "session-" + user.getId());
	}

	private FakeWebSocketSession connect(Long poll, User user, String httpSessionId) {
		FakeWebSocketSession session = new FakeWebSocketSession(poll, orgId, user.getId(), httpSessionId);
		chatHub.register(session);
		return session;
	}

	private String messagesUrl() {
		return "/api/polls/" + pollId + "/messages";
	}

	private ResultActions send(User user, String body) throws Exception {
		return mockMvc.perform(post(messagesUrl()).with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"" + body + "\"}"));
	}

	private ResultActions edit(User user, Long messageId, String body) throws Exception {
		return mockMvc.perform(put(messagesUrl() + "/" + messageId).with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"" + body + "\"}"));
	}

	private ResultActions remove(User user, Long messageId) throws Exception {
		return mockMvc.perform(delete(messagesUrl() + "/" + messageId).with(loginAs(user)).with(xsrf()));
	}
}
