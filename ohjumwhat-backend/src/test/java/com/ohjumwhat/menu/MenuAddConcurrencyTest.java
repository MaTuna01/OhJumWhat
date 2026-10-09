package com.ohjumwhat.menu;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 같은 이름의 메뉴를 동시에 추가할 때. exists → INSERT 사이가 비어 있어 두 번째는 (poll, name) UNIQUE 위반이 되고,
 * 사용자는 「이미 있는 메뉴예요」 대신 「다른 사람의 변경과 겹쳤어요」를 본다(이슈 #143, 문구 결함 기록).
 */
class MenuAddConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	User kim;

	User lee;

	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		inviteService.join(org.inviteToken(), lee.getId());
		pollId = pollService.create(org.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	/** 결함 기록: 동시 추가의 두 번째는 409이지만 문구가 「다른 사람의 변경과 겹쳤어요」다. 수정 이슈에서 「이미 있는 메뉴예요.」로 뒤집는다. */
	@Test
	void 같은_메뉴를_동시에_추가하면_두_번째는_겹침_문구다_현재_동작() throws Exception {
		RowLock first = holdUncommitted("insert into menu_options (poll_id, created_by, name) values (?, ?, ?)", true, pollId,
				kim.getId(), "김치찌개");
		Future<MockHttpServletResponse> second = inThread(() -> mockMvc
			.perform(post("/api/polls/{pollId}/options", pollId).with(loginAs(lee)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"김치찌개\"}"))
			.andReturn()
			.getResponse());
		awaitLockWait("%insert into menu_options%");

		first.release();
		MockHttpServletResponse response = await(second);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("다른 사람의 변경과 겹쳤어요");
		assertThat(jdbcTemplate.queryForObject("select count(*) from menu_options", Long.class)).isEqualTo(1);
	}

	/** 비교: 차례로 추가하면 「이미 있는 메뉴예요」다. 띄어쓰기·대소문자만 다른 이름은 둘 다 들어간다(UNIQUE도 exists도 글자 그대로). */
	@Test
	void 차례로_추가하면_이미_있는_메뉴_문구이고_띄어쓰기가_다르면_둘_다_들어간다() throws Exception {
		mockMvc.perform(post("/api/polls/{pollId}/options", pollId).with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"김치찌개\"}"));

		MockHttpServletResponse same = mockMvc
			.perform(post("/api/polls/{pollId}/options", pollId).with(loginAs(lee)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \" 김치찌개 \"}"))
			.andReturn()
			.getResponse();
		MockHttpServletResponse spaced = mockMvc
			.perform(post("/api/polls/{pollId}/options", pollId).with(loginAs(lee)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"김치 찌개\"}"))
			.andReturn()
			.getResponse();

		assertThat(same.getStatus()).isEqualTo(409);
		assertThat(same.getContentAsString(StandardCharsets.UTF_8)).contains("이미 있는 메뉴예요");
		assertThat(spaced.getStatus()).isEqualTo(201);
		assertThat(jdbcTemplate.queryForObject("select count(*) from menu_options", Long.class)).isEqualTo(2);
	}
}
