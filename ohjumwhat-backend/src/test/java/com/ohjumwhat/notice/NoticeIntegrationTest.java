package com.ohjumwhat.notice;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class NoticeIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	NoticeRepository noticeRepository;

	User kim;

	User lee;

	/** 두 회원이 가입한 시각. users.created_at은 시계가 아니라 실제 시각이라, 공지 게시 시각은 이것을 기준으로 정한다. */
	Instant joinedAt;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		joinedAt = lee.getCreatedAt();
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/notices/unread")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/notices/seen").with(xsrf())).andExpect(status().isUnauthorized());
	}

	@Test
	void 새_소식은_최신순으로_10개씩_준다() throws Exception {
		for (int i = 1; i <= 11; i++) {
			noticeRepository.save(Notice.note(i + "번째 소식", "본문", null, minutesAfterJoin(i)));
		}

		mockMvc.perform(get("/api/notices").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notices", hasSize(10)))
			.andExpect(jsonPath("$.notices[0].title").value("11번째 소식"))
			.andExpect(jsonPath("$.notices[9].title").value("2번째 소식"))
			.andExpect(jsonPath("$.hasMore").value(true));
		mockMvc.perform(get("/api/notices").param("page", "1").with(loginAs(kim)))
			.andExpect(jsonPath("$.notices[*].title", contains("1번째 소식")))
			.andExpect(jsonPath("$.hasMore").value(false));
		mockMvc.perform(get("/api/notices").param("page", "-1").with(loginAs(kim)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("페이지 번호가 올바르지 않아요."));
	}

	@Test
	void 같은_시각에_게시된_공지는_나중에_넣은_것이_먼저다() throws Exception {
		Instant at = minutesAfterJoin(1);
		noticeRepository.save(Notice.release("1.5.0", "1.5.0 소식", "본문", at));
		noticeRepository.save(Notice.release("1.6.0", "1.6.0 소식", "본문", at));

		mockMvc.perform(get("/api/notices").with(loginAs(kim)))
			.andExpect(jsonPath("$.notices[*].version", contains("1.6.0", "1.5.0")))
			.andExpect(jsonPath("$.notices[0].kind").value("RELEASE"));
	}

	@Test
	void 가입하기_전에_게시된_공지는_안_읽은_공지가_아니다() throws Exception {
		noticeRepository.save(Notice.release("1.4.0", "지난 업데이트", "본문", joinedAt.minus(Duration.ofDays(1))));

		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(0))
			.andExpect(jsonPath("$.latest").value(nullValue()));
		mockMvc.perform(get("/api/notices").with(loginAs(kim)))
			.andExpect(jsonPath("$.notices[0].unread").value(false));
	}

	@Test
	void 가입한_뒤_게시된_공지는_안_읽은_공지이고_새_소식을_보면_모두_읽은_것이_된다() throws Exception {
		noticeRepository.save(Notice.release("1.5.0", "별명이 생겼어요", "본문", minutesAfterJoin(1)));
		Notice note = noticeRepository.save(Notice.note("새 소식을 시작해요", "본문", null, minutesAfterJoin(2)));

		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim)))
			.andExpect(jsonPath("$.count").value(2))
			.andExpect(jsonPath("$.latest.id").value(note.getId()))
			.andExpect(jsonPath("$.latest.kind").value("NOTE"))
			.andExpect(jsonPath("$.latest.version").value(nullValue()))
			.andExpect(jsonPath("$.latest.title").value("새 소식을 시작해요"));
		mockMvc.perform(get("/api/notices").with(loginAs(kim)))
			.andExpect(jsonPath("$.notices[*].unread", contains(true, true)));

		clock.set(minutesAfterJoin(3));
		mockMvc.perform(post("/api/notices/seen").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim)))
			.andExpect(jsonPath("$.count").value(0))
			.andExpect(jsonPath("$.latest").value(nullValue()));
		mockMvc.perform(get("/api/notices").with(loginAs(kim)))
			.andExpect(jsonPath("$.notices[*].unread", contains(false, false)));

		// 본 뒤에 새로 올라온 공지만 다시 안 읽은 공지가 된다.
		noticeRepository.save(Notice.note("점검 안내", "본문", null, minutesAfterJoin(4)));
		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim)))
			.andExpect(jsonPath("$.count").value(1))
			.andExpect(jsonPath("$.latest.title").value("점검 안내"));
	}

	@Test
	void 읽음은_회원마다_따로다() throws Exception {
		noticeRepository.save(Notice.release("1.5.0", "별명이 생겼어요", "본문", minutesAfterJoin(1)));

		clock.set(minutesAfterJoin(2));
		mockMvc.perform(post("/api/notices/seen").with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/notices/unread").with(loginAs(lee))).andExpect(jsonPath("$.count").value(0));
		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim))).andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void 회원이_없어진_세션은_401이다() throws Exception {
		User gone = userRepository.save(new User("sub-gone", "gone@example.com", "탈퇴", null));
		userRepository.delete(gone);

		mockMvc.perform(get("/api/notices/unread").with(loginAs(gone))).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/notices/seen").with(loginAs(gone)).with(xsrf()))
			.andExpect(status().isUnauthorized());
	}

	private Instant minutesAfterJoin(int minutes) {
		return joinedAt.plus(Duration.ofMinutes(minutes));
	}
}
