package com.ohjumwhat.notice;

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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class NoticeAdminIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	NoticeRepository noticeRepository;

	User admin;

	User kim;

	/** users.created_at은 시계가 아니라 실제 시각이라, 게시 시각은 이것을 기준으로 정한다. */
	Instant joinedAt;

	@BeforeEach
	void setUp() {
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		joinedAt = kim.getCreatedAt().truncatedTo(ChronoUnit.MICROS); // DB(timestamptz)는 마이크로초까지 저장한다
		clock.set(minutesAfterJoin(1));
	}

	@Test
	void 공지_관리_API는_관리자만_쓸_수_있다() throws Exception {
		mockMvc.perform(post("/api/admin/notices").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content(json("점검 안내", "본문"))).andExpect(status().isUnauthorized());
		write(kim, "점검 안내", "본문").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("권한이 없어요."));
		assertThat(noticeRepository.count()).isZero();
	}

	@Test
	void 관리자는_개발자_노트를_쓰고_고치고_지운다() throws Exception {
		String response = write(admin, "  점검 안내  ", "  오늘 밤 10시에 잠깐 멈춰요.\n- 5분 정도 걸려요  ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("NOTE"))
			.andExpect(jsonPath("$.version").value(nullValue()))
			.andExpect(jsonPath("$.title").value("점검 안내"))
			.andExpect(jsonPath("$.body").value("오늘 밤 10시에 잠깐 멈춰요.\n- 5분 정도 걸려요"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Notice note = noticeRepository.findAll().getFirst();
		assertThat(response).contains("\"id\":" + note.getId());
		assertThat(note.getCreatedBy()).isEqualTo(admin.getId());
		assertThat(note.getPublishedAt()).isEqualTo(minutesAfterJoin(1));
		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim))).andExpect(jsonPath("$.count").value(1));

		// 회원이 읽은 뒤에 고쳐도 다시 알리지 않는다(게시 시각 그대로).
		clock.set(minutesAfterJoin(2));
		mockMvc.perform(post("/api/notices/seen").with(loginAs(kim)).with(xsrf())).andExpect(status().isNoContent());
		clock.set(minutesAfterJoin(3));
		edit(admin, note.getId(), "점검 안내(시간 변경)", "오늘 밤 11시로 바뀌었어요.")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("점검 안내(시간 변경)"));
		assertThat(noticeRepository.findById(note.getId()).orElseThrow().getPublishedAt())
			.isEqualTo(minutesAfterJoin(1));
		mockMvc.perform(get("/api/notices/unread").with(loginAs(kim))).andExpect(jsonPath("$.count").value(0));

		mockMvc.perform(delete("/api/admin/notices/" + note.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/notices").with(loginAs(kim))).andExpect(jsonPath("$.notices", hasSize(0)));
	}

	@Test
	void 업데이트_글은_콘솔에서_고치거나_지울_수_없다() throws Exception {
		Notice release = noticeRepository.save(Notice.release("1.5.0", "별명이 생겼어요", "본문", minutesAfterJoin(1)));

		edit(admin, release.getId(), "바꾼 제목", "바꾼 본문")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("업데이트 글은 저장소 파일에서 고쳐요."));
		mockMvc.perform(delete("/api/admin/notices/" + release.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isConflict());
		assertThat(noticeRepository.findById(release.getId()).orElseThrow().getTitle()).isEqualTo("별명이 생겼어요");
	}

	@Test
	void 없는_공지는_404다() throws Exception {
		edit(admin, 999L, "제목", "본문").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("공지를 찾을 수 없어요."));
		mockMvc.perform(delete("/api/admin/notices/999").with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 제목과_내용을_검사한다() throws Exception {
		write(admin, "  ", "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("제목을 입력해 주세요."));
		write(admin, "가".repeat(101), "본문").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("제목은 100자 이하로 입력해 주세요."));
		write(admin, "제목", "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));
		write(admin, "제목", "가".repeat(5001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용은 5,000자 이하로 입력해 주세요."));
		write(admin, "가".repeat(100), "가".repeat(5000)).andExpect(status().isCreated());
	}

	private ResultActions write(User user, String title, String body) throws Exception {
		return mockMvc.perform(post("/api/admin/notices").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(title, body)));
	}

	private ResultActions edit(User user, Long noticeId, String title, String body) throws Exception {
		return mockMvc.perform(put("/api/admin/notices/" + noticeId).with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(title, body)));
	}

	/** 테스트 값에는 따옴표가 없으므로 줄바꿈만 이스케이프한다. */
	private static String json(String title, String body) {
		return "{\"title\": \"" + title + "\", \"body\": \"" + body.replace("\n", "\\n") + "\"}";
	}

	private Instant minutesAfterJoin(int minutes) {
		return joinedAt.plus(Duration.ofMinutes(minutes));
	}
}
