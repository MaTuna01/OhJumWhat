package com.ohjumwhat.user;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ohjumwhat.ConcurrencyTest;

/**
 * 로그인(회원 행 전체 UPDATE)과 별명 바꾸기(update 쿼리)가 진짜 두 스레드에서 겹쳐도 별명을 되쓰지 않는다. 별명 같은 컬럼이
 * 엔티티에서 insertable/updatable=false라 로그인의 UPDATE 문장에 아예 들어가지 않기 때문이다(이슈 #143, 안전 확인).
 * 기존 테스트(NicknameIntegrationTest 등)는 한 트랜잭션 안에 끼워 넣는 방식이라 여기서 순서 양쪽을 실제 잠금 대기로 본다.
 */
class LoginProfileConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserService userService;

	User kim;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
	}

	@Test
	void 로그인이_먼저_커밋돼도_뒤에_커밋된_별명이_남고_구글_이름도_갱신된다() throws Exception {
		RowLock lock = holdLock("select id from users where id = ? for update", kim.getId());
		Future<User> login = inThread(() -> userService.login("sub-kim", "kim@example.com", true, "김철수(새 이름)", null));
		awaitLockWait("%update users %set%", 1);
		Future<MockHttpServletResponse> nickname = inThread(this::changeNickname);
		awaitLockWait("%update users %set%", 2);

		lock.release();
		await(login);
		assertThat(await(nickname).getStatus()).isEqualTo(200);

		assertThat(row()).containsEntry("name", "김철수(새 이름)").containsEntry("nickname", "철수");
	}

	@Test
	void 별명이_먼저_커밋되고_로그인이_뒤에_커밋돼도_별명이_남는다() throws Exception {
		RowLock lock = holdLock("select id from users where id = ? for update", kim.getId());
		Future<MockHttpServletResponse> nickname = inThread(this::changeNickname);
		awaitLockWait("%update users %set%", 1);
		Future<User> login = inThread(() -> userService.login("sub-kim", "kim@example.com", true, "김철수(새 이름)", null));
		awaitLockWait("%update users %set%", 2);

		lock.release();
		assertThat(await(nickname).getStatus()).isEqualTo(200);
		await(login);

		assertThat(row()).containsEntry("name", "김철수(새 이름)").containsEntry("nickname", "철수");
	}

	private MockHttpServletResponse changeNickname() throws Exception {
		return mockMvc.perform(put("/api/me/nickname").with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"철수\"}")).andReturn().getResponse();
	}

	private Map<String, Object> row() {
		return jdbcTemplate.queryForMap("select name, nickname from users where id = ?", kim.getId());
	}
}
