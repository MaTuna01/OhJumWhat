package com.ohjumwhat.guestbook;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserService;

/** 방명록 알림: 내 방명록의 새 글 수와 제한된 내 글의 경고, 본 시각·확인 시각 */
class GuestbookAlertsIntegrationTest extends GuestbookTestBase {

	@Autowired
	UserService userService;

	@Test
	void 처음에는_내_방명록의_모든_글이_새_글이고_지운_글은_세지_않는다() throws Exception {
		long first = writeOk(lee, kim, "하나");
		writeOk(park, kim, "둘");
		writeOk(choi, kim, "셋");

		alerts(kim).andExpect(status().isOk())
			.andExpect(jsonPath("$.newEntryCount").value(3))
			.andExpect(jsonPath("$.warnings", hasSize(0)));
		deleteEntry(kim, first).andExpect(status().isNoContent());
		alerts(kim).andExpect(jsonPath("$.newEntryCount").value(2));
		// 남의 방명록에 쓴 글은 내 새 글이 아니다.
		alerts(lee).andExpect(jsonPath("$.newEntryCount").value(0));
	}

	@Test
	void 본_시각은_뒤로_가지_않고_지금보다_뒤면_지금으로_자른다() throws Exception {
		writeOk(lee, kim, "하나"); // 03:00:00
		writeOk(park, kim, "둘"); // 03:00:05, 시계는 03:00:10

		seen(kim, Instant.parse("2026-10-06T03:00:05Z")).andExpect(status().isNoContent());
		alerts(kim).andExpect(jsonPath("$.newEntryCount").value(0));
		list(kim, kim).andExpect(jsonPath("$.seenAt").value("2026-10-06T03:00:05Z"));

		// 더 이른 시각으로는 돌아가지 않는다.
		seen(kim, Instant.parse("2026-10-06T03:00:00Z")).andExpect(status().isNoContent());
		list(kim, kim).andExpect(jsonPath("$.seenAt").value("2026-10-06T03:00:05Z"));
		alerts(kim).andExpect(jsonPath("$.newEntryCount").value(0));

		// 미래 시각은 지금(03:00:10)으로 자른다. 그래서 그 뒤에 쓰인 글은 새 글이다.
		seen(kim, Instant.parse("2026-10-07T03:00:00Z")).andExpect(status().isNoContent());
		list(kim, kim).andExpect(jsonPath("$.seenAt").value("2026-10-06T03:00:10Z"));
		later(5);
		writeOk(lee, kim, "셋");
		alerts(kim).andExpect(jsonPath("$.newEntryCount").value(1));
	}

	@Test
	void 시각이_없으면_400이다() throws Exception {
		mockMvc.perform(post("/api/guestbook/seen").with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("시각이 올바르지 않아요."));
	}

	@Test
	void 경고는_확인하면_사라지고_새로_제한된_글은_다시_보인다() throws Exception {
		User admin = admin();
		long first = writeOk(lee, kim, "첫 글"); // 03:00:00
		long second = writeOk(lee, kim, "둘째 글"); // 03:00:05, 시계는 03:00:10
		report(kim, first, "{}").andExpect(status().isNoContent());
		report(kim, second, "{}").andExpect(status().isNoContent());

		restrict(admin, reportIdOf(admin, first)).andExpect(status().isNoContent());
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(1)))
			.andExpect(jsonPath("$.warnings[0].entryId").value(first))
			.andExpect(jsonPath("$.warnings[0].owner.userId").value(kim.getId()))
			.andExpect(jsonPath("$.warnings[0].owner.name").value("김철수"))
			.andExpect(jsonPath("$.warnings[0].owner.profileImageUrl").value(nullValue()))
			.andExpect(jsonPath("$.warnings[0].body").value("첫 글"))
			.andExpect(jsonPath("$.warnings[0].writtenAt").value("2026-10-06T03:00:00Z"))
			.andExpect(jsonPath("$.warnings[0].restrictedAt").value("2026-10-06T03:00:10Z"));
		// 주인에게는 경고가 가지 않는다.
		alerts(kim).andExpect(jsonPath("$.warnings", hasSize(0)));

		ack(lee, Instant.parse("2026-10-06T03:00:10Z")).andExpect(status().isNoContent());
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(0)));

		later(5);
		restrict(admin, reportIdOf(admin, second)).andExpect(status().isNoContent());
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(1)))
			.andExpect(jsonPath("$.warnings[0].entryId").value(second))
			.andExpect(jsonPath("$.warnings[0].restrictedAt").value("2026-10-06T03:00:15Z"));
		// 더 이른 시각으로는 돌아가지 않고, 미래 시각은 지금으로 자른다.
		ack(lee, Instant.parse("2026-10-06T03:00:00Z")).andExpect(status().isNoContent());
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(1)));
		ack(lee, Instant.parse("2026-10-07T00:00:00Z")).andExpect(status().isNoContent());
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(0)));
		assertThat(userRepository.findById(lee.getId()).orElseThrow().getGuestbookWarningsSeenAt())
			.isEqualTo(Instant.parse("2026-10-06T03:00:15Z"));
	}

	@Test
	void 본_시각을_올린_뒤_다시_로그인하거나_회원을_저장해도_그대로다() throws Exception {
		writeOk(lee, kim, "하나"); // 03:00:00, 시계는 03:00:05
		seen(kim, Instant.parse("2026-10-06T03:00:00Z")).andExpect(status().isNoContent());
		ack(kim, Instant.parse("2026-10-06T03:00:05Z")).andExpect(status().isNoContent());

		// 본 시각을 모르는 옛 엔티티를 저장해도 되쓰지 않는다.
		userRepository.save(kim);
		// 로그인은 회원 행을 다시 쓴다(이름·최근 로그인 시각).
		userService.login("sub-kim", "kim@example.com", true, "김철수(새 이름)", null);

		User saved = userRepository.findById(kim.getId()).orElseThrow();
		assertThat(saved.getLastLoginAt()).isEqualTo(Instant.parse("2026-10-06T03:00:05Z"));
		assertThat(saved.getGuestbookSeenAt()).isEqualTo(Instant.parse("2026-10-06T03:00:00Z"));
		assertThat(saved.getGuestbookWarningsSeenAt()).isEqualTo(Instant.parse("2026-10-06T03:00:05Z"));
		list(kim, kim).andExpect(jsonPath("$.seenAt").value("2026-10-06T03:00:00Z"));
		alerts(kim).andExpect(jsonPath("$.newEntryCount").value(0));
	}

	@Test
	void 회원_행이_없으면_401이다() throws Exception {
		User ghost = userRepository.save(new User("sub-ghost", "ghost@example.com", "유령", null));
		jdbcTemplate.update("delete from users where id = ?", ghost.getId());

		alerts(ghost).andExpect(status().isUnauthorized());
		seen(ghost, clock.instant()).andExpect(status().isUnauthorized());
		ack(ghost, clock.instant()).andExpect(status().isUnauthorized());
	}
}
