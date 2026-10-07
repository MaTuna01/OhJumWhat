package com.ohjumwhat.sanction;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.user.User;

/** 본인의 제재 안내: 보지 않은 것만 오래된 순으로, 본 것은 내 것만 표시한다. */
class SanctionAlertsIntegrationTest extends SanctionTestBase {

	@Test
	void 보지_않은_내_제재를_오래된_순으로_상태와_함께_준다() throws Exception {
		fillProfile(kim);
		long chat = sanction(kim, """
				{"restrictions": ["CHAT"], "resets": ["NICKNAME"], "days": 7, "reason": "ABUSE", "note": "욕설을 했어요"}""");
		long lifted = restrict(kim, null, Restriction.LETTER);
		long warning = sanction(kim, "{\"reason\": \"ETC\"}");
		long resetOnly = sanction(kim, "{\"resets\": [\"PHOTO\", \"INTRO\"], \"reason\": \"PROFILE\"}");
		long expired = restrict(kim, 1, Restriction.GUESTBOOK);
		restrict(lee, 7, Restriction.CHAT);
		clock.set(2026, 10, 7, 12, 0);
		lift(lifted).andExpect(status().isOk());
		clock.set(2026, 10, 8, 12, 0);

		alerts(kim).andExpect(status().isOk())
			.andExpect(jsonPath("$.sanctions[*].id", contains((int) chat, (int) lifted, (int) warning, (int) resetOnly,
					(int) expired)))
			.andExpect(jsonPath("$.sanctions[*].status", contains("ACTIVE", "LIFTED", "WARNING", "RESET_ONLY", "EXPIRED")))
			.andExpect(jsonPath("$.sanctions[0].restrictions", contains("CHAT")))
			.andExpect(jsonPath("$.sanctions[0].resets", contains("NICKNAME")))
			.andExpect(jsonPath("$.sanctions[0].reason").value("ABUSE"))
			.andExpect(jsonPath("$.sanctions[0].note").value("욕설을 했어요"))
			.andExpect(jsonPath("$.sanctions[0].createdAt").value("2026-10-07T02:00:00Z"))
			.andExpect(jsonPath("$.sanctions[0].endsAt").value("2026-10-14T02:00:00Z"))
			.andExpect(jsonPath("$.sanctions[0].liftedAt").value(nullValue()))
			.andExpect(jsonPath("$.sanctions[1].endsAt").value(nullValue()))
			.andExpect(jsonPath("$.sanctions[1].liftedAt").value("2026-10-07T03:00:00Z"))
			.andExpect(jsonPath("$.sanctions[3].restrictions", empty()))
			.andExpect(jsonPath("$.sanctions[3].resets", contains("PHOTO", "INTRO")));
		alerts(lee).andExpect(jsonPath("$.sanctions", hasSize(1)));
		alerts(admin).andExpect(jsonPath("$.sanctions", empty()));
	}

	@Test
	void 본_것은_다시_주지_않고_남의_것은_표시하지_않는다() throws Exception {
		long first = restrict(kim, 7, Restriction.CHAT);
		long second = sanction(kim, "{\"reason\": \"ETC\"}");
		long leeSanction = restrict(lee, 7, Restriction.CHAT);

		clock.set(2026, 10, 7, 11, 30);
		seen(kim, "[" + first + ", " + leeSanction + ", 9999]").andExpect(status().isNoContent());

		alerts(kim).andExpect(jsonPath("$.sanctions[*].id", contains((int) second)));
		alerts(lee).andExpect(jsonPath("$.sanctions[*].id", contains((int) leeSanction)));
		assertThat(sanctionRepository.findById(first).orElseThrow().getSeenAt())
			.isEqualTo(Instant.parse("2026-10-07T02:30:00Z"));
		assertThat(sanctionRepository.findById(leeSanction).orElseThrow().getSeenAt()).isNull();

		// 이미 본 것은 처음 본 시각 그대로 둔다.
		clock.set(2026, 10, 7, 12, 0);
		seen(kim, "[" + first + ", " + second + "]").andExpect(status().isNoContent());
		assertThat(sanctionRepository.findById(first).orElseThrow().getSeenAt())
			.isEqualTo(Instant.parse("2026-10-07T02:30:00Z"));
		alerts(kim).andExpect(jsonPath("$.sanctions", empty()));
		// 봤어도 진행 중인 제재는 내 정보에 그대로 있다.
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.sanctions", hasSize(1)));
	}

	@Test
	void 확인할_ID가_없거나_50개를_넘으면_400이다() throws Exception {
		String fifty = LongStream.rangeClosed(1, 50).mapToObj(Long::toString).collect(Collectors.joining(","));
		seen(kim, "[]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("확인한 안내가 올바르지 않아요."));
		seen(kim, "null").andExpect(status().isBadRequest());
		seen(kim, "[1, null]").andExpect(status().isBadRequest());
		seen(kim, "[" + fifty + ", 51]").andExpect(status().isBadRequest());
		seen(kim, "[" + fifty + "]").andExpect(status().isNoContent());
		mockMvc.perform(post("/api/sanctions/seen").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\": [1]}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 안내는_최대_20건이다() throws Exception {
		for (int i = 0; i < 21; i++) {
			sanction(kim, "{\"reason\": \"ETC\"}");
		}
		alerts(kim).andExpect(jsonPath("$.sanctions", hasSize(20)))
			.andExpect(jsonPath("$.sanctions[0].id").value(1))
			.andExpect(jsonPath("$.sanctions[19].id").value(20));
	}

	private ResultActions alerts(User user) throws Exception {
		return mockMvc.perform(get("/api/sanctions/alerts").with(loginAs(user)));
	}

	private ResultActions seen(User user, String ids) throws Exception {
		return mockMvc.perform(post("/api/sanctions/seen").with(loginAs(user))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\": " + ids + "}"));
	}
}
