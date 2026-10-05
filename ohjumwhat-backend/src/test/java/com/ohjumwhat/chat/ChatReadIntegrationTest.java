package com.ohjumwhat.chat;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 채팅 안 읽은 메시지: 읽은 위치(PUT …/messages/read), 채팅 목록의 lastReadId, 오늘 투표 카드의 unreadMessages */
class ChatReadIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	ChatMessageRepository chatMessageRepository;

	User admin;

	User kim;

	User lee;

	User park;

	Long orgId;

	String inviteToken;

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
		inviteToken = dev.inviteToken();
		inviteService.join(inviteToken, lee.getId());
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@Test
	void 읽은_기록이_없으면_남의_메시지는_모두_안_읽음이다() throws Exception {
		message(kim, "김치찌개 어때요?");
		message(kim, "돈가스도 좋아요");
		message(lee, "좋아요");

		mockMvc.perform(get(messagesUrl()).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages", hasSize(3)))
			.andExpect(jsonPath("$.lastReadId").value(0));
		today(lee).andExpect(jsonPath("$[0].unreadMessages").value(2));
		today(kim).andExpect(jsonPath("$[0].unreadMessages").value(1));
	}

	@Test
	void 읽은_위치를_저장하면_채팅_목록과_오늘_투표에_반영된다() throws Exception {
		message(kim, "김치찌개 어때요?");
		Long second = message(kim, "돈가스도 좋아요");
		message(kim, "국밥은요?");

		markRead(lee, second).andExpect(status().isNoContent());

		mockMvc.perform(get(messagesUrl()).with(loginAs(lee)))
			.andExpect(jsonPath("$.lastReadId").value(second));
		today(lee).andExpect(jsonPath("$[0].unreadMessages").value(1));
		// 읽은 위치는 사람마다 따로다. 관리자 콘솔의 채팅 조회에는 읽은 위치가 없다.
		assertThat(lastReadId(kim)).isZero();
		mockMvc.perform(get("/api/admin/polls/" + pollId + "/messages").with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lastReadId").value(0));
	}

	@Test
	void 읽은_위치는_뒤로_가지_않고_그_투표의_마지막_메시지를_넘지_않는다() throws Exception {
		Long first = message(kim, "김치찌개 어때요?");
		Long second = message(kim, "돈가스도 좋아요");
		Long dinnerId = pollService.create(orgId, kim.getId(), new PollRequest("저녁", "18:00")).id();
		Long dinnerMessage = message(kim, dinnerId, "삼겹살 어때요?");

		markRead(lee, second).andExpect(status().isNoContent());
		markRead(lee, first).andExpect(status().isNoContent());
		assertThat(lastReadId(lee)).isEqualTo(second);

		// 다른 투표의 더 뒤 메시지 ID나 없는 ID를 보내도 이 투표의 마지막 메시지까지만 읽는다.
		markRead(lee, dinnerMessage).andExpect(status().isNoContent());
		assertThat(lastReadId(lee)).isEqualTo(second);
		markRead(lee, 999_999L).andExpect(status().isNoContent());
		assertThat(lastReadId(lee)).isEqualTo(second);
		today(lee).andExpect(jsonPath("$[0].unreadMessages").value(0));

		// 그래서 그 뒤에 온 메시지는 안 읽음이다.
		message(kim, "국밥은요?");
		today(lee)
			.andExpect(jsonPath("$[0].unreadMessages").value(1))
			.andExpect(jsonPath("$[1].unreadMessages").value(1));
	}

	@Test
	void 내_글과_지운_글은_세지_않고_탈퇴한_사용자의_글은_센다() throws Exception {
		inviteService.join(inviteToken, park.getId());
		message(kim, "김치찌개 어때요?");
		Long deletedByLee = message(lee, "잘못 보냈어요");
		Long deletedByAdmin = message(lee, "부적절한 글");
		message(park, "저는 국밥이요");
		message(lee, "1층에서 만나요");

		mockMvc.perform(delete(messagesUrl() + "/" + deletedByLee).with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/admin/chat-messages/" + deletedByAdmin).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/admin/users/" + park.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject("select count(*) from chat_messages where user_id is null",
				Integer.class)).isEqualTo(1);

		// 김철수: 내 글은 빼고, 지운 글 2개도 빼고, 탈퇴한 사용자의 글 + 이영희의 남은 글
		today(kim).andExpect(jsonPath("$[0].unreadMessages").value(2));
		// 이영희: 김철수의 글 + 탈퇴한 사용자의 글
		today(lee).andExpect(jsonPath("$[0].unreadMessages").value(2));
	}

	@Test
	void 보내면_보낸_메시지까지_읽은_것으로_한다() throws Exception {
		message(lee, "김치찌개 어때요?");
		message(lee, "돈가스도 좋아요");
		String sent = mockMvc.perform(post(messagesUrl()).with(loginAs(kim)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\": \"좋아요\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long sentId = ((Number) JsonPath.read(sent, "$.id")).longValue();

		assertThat(lastReadId(kim)).isEqualTo(sentId);
		today(kim).andExpect(jsonPath("$[0].unreadMessages").value(0));
		today(lee).andExpect(jsonPath("$[0].unreadMessages").value(1));

		message(lee, "1층에서 만나요");
		today(kim).andExpect(jsonPath("$[0].unreadMessages").value(1));
	}

	@Test
	void 멤버가_아니거나_투표가_없으면_404이고_위치가_올바르지_않으면_400() throws Exception {
		Long messageId = message(kim, "김치찌개 어때요?");

		markRead(park, messageId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("투표를 찾을 수 없어요."));
		read(lee, 999_999L, "{\"lastReadId\": " + messageId + "}").andExpect(status().isNotFound());
		read(lee, pollId, "{\"lastReadId\": -1}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("읽은 위치가 올바르지 않아요."));
		read(lee, pollId, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("읽은 위치가 올바르지 않아요."));
		assertThat(chatReadCount()).isZero();
	}

	@Test
	void 채팅이_닫힌_뒤에도_읽은_위치를_저장한다() throws Exception {
		Long messageId = message(kim, "김치찌개 어때요?");

		clock.set(2026, 9, 30, 13, 0); // 채팅은 12:50에 닫혔다.
		markRead(lee, messageId).andExpect(status().isNoContent());

		assertThat(lastReadId(lee)).isEqualTo(messageId);
		today(lee)
			.andExpect(jsonPath("$[0].status").value("CLOSED"))
			.andExpect(jsonPath("$[0].unreadMessages").value(0));
	}

	@Test
	void 메시지가_없는_투표나_0에는_읽은_위치를_만들지_않는다() throws Exception {
		markRead(lee, 5L).andExpect(status().isNoContent());
		assertThat(chatReadCount()).isZero();
		assertThat(lastReadId(lee)).isZero();

		message(kim, "김치찌개 어때요?");
		markRead(lee, 0L).andExpect(status().isNoContent());
		assertThat(chatReadCount()).isZero();
		today(lee).andExpect(jsonPath("$[0].unreadMessages").value(1));
	}

	@Test
	void 오늘_투표마다_내_읽은_위치로_따로_센다() throws Exception {
		inviteService.join(inviteToken, park.getId());
		Long dinnerId = pollService.create(orgId, kim.getId(), new PollRequest("저녁", "18:00")).id();
		message(park, "김치찌개 어때요?");
		message(kim, "좋아요");
		Long leeLast = message(lee, "저도요");
		message(kim, dinnerId, "삼겹살 어때요?");

		markRead(lee, leeLast).andExpect(status().isNoContent());

		today(lee)
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].title").value("점심"))
			.andExpect(jsonPath("$[0].closesAt").value("2026-09-30T02:50:00Z"))
			.andExpect(jsonPath("$[0].chatClosesAt").value("2026-09-30T03:50:00Z"))
			.andExpect(jsonPath("$[0].unreadMessages").value(0))
			.andExpect(jsonPath("$[1].title").value("저녁"))
			.andExpect(jsonPath("$[1].chatClosesAt").value("2026-09-30T10:00:00Z"))
			.andExpect(jsonPath("$[1].unreadMessages").value(1));
		// 이영희가 읽은 위치는 김철수의 수에 영향을 주지 않는다(박민수·이영희의 글).
		today(kim)
			.andExpect(jsonPath("$[0].unreadMessages").value(2))
			.andExpect(jsonPath("$[1].unreadMessages").value(0));
	}

	@Test
	void 조직을_떠나도_남고_투표나_회원을_지우면_함께_지운다() throws Exception {
		Long lunchMessage = message(kim, "김치찌개 어때요?");
		Long dinnerId = pollService.create(orgId, kim.getId(), new PollRequest("저녁", "18:00")).id();
		Long dinnerMessage = message(kim, dinnerId, "삼겹살 어때요?");
		markRead(kim, lunchMessage).andExpect(status().isNoContent());
		markRead(lee, lunchMessage).andExpect(status().isNoContent());
		read(lee, dinnerId, "{\"lastReadId\": " + dinnerMessage + "}").andExpect(status().isNoContent());
		assertThat(chatReadCount()).isEqualTo(3);

		mockMvc.perform(delete("/api/orgs/" + orgId + "/membership").with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isOk());
		assertThat(chatReadCount()).isEqualTo(3);

		mockMvc.perform(delete("/api/polls/" + pollId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject("select count(*) from chat_reads where poll_id = ?", Integer.class,
				pollId)).isZero();
		assertThat(chatReadCount()).isEqualTo(1);

		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(chatReadCount()).isZero();
	}

	private String messagesUrl() {
		return "/api/polls/" + pollId + "/messages";
	}

	/** 보내기 API를 거치지 않고 메시지를 넣는다(보내면 내 글까지 읽음으로 바뀌므로). */
	private Long message(User user, String body) {
		return message(user, pollId, body);
	}

	private Long message(User user, Long poll, String body) {
		return chatMessageRepository.save(new ChatMessage(poll, user.getId(), body, Instant.now(clock))).getId();
	}

	private ResultActions markRead(User user, long lastReadId) throws Exception {
		return read(user, pollId, "{\"lastReadId\": " + lastReadId + "}");
	}

	private ResultActions read(User user, Long poll, String json) throws Exception {
		return mockMvc.perform(put("/api/polls/" + poll + "/messages/read").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private long lastReadId(User user) throws Exception {
		String body = mockMvc.perform(get(messagesUrl()).with(loginAs(user)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.lastReadId")).longValue();
	}

	private ResultActions today(User user) throws Exception {
		return mockMvc.perform(get("/api/orgs/" + orgId + "/polls/today").with(loginAs(user)))
			.andExpect(status().isOk());
	}

	private int chatReadCount() {
		return jdbcTemplate.queryForObject("select count(*) from chat_reads", Integer.class);
	}
}
