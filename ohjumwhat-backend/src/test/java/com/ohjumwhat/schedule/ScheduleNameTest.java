package com.ohjumwhat.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ScheduleNameTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 8);

	@Test
	void 날짜_토큰을_그날의_날짜로_바꾼다() {
		assertThat(ScheduleName.render("${오늘날짜} 오점왓?!", DAY)).isEqualTo("2026-10-08 오점왓?!");
		assertThat(ScheduleName.render("점심 ${오늘날짜} / ${오늘날짜}", DAY)).isEqualTo("점심 2026-10-08 / 2026-10-08");
		assertThat(ScheduleName.render("점심", DAY)).isEqualTo("점심");
	}

	@Test
	void 오타_토큰은_글자로_둔다() {
		assertThat(ScheduleName.render("${오늘 날짜} ${날짜} $오늘날짜 {오늘날짜}", DAY))
			.isEqualTo("${오늘 날짜} ${날짜} $오늘날짜 {오늘날짜}");
	}

	@Test
	void 길이는_토큰을_10자로_센다() {
		assertThat(ScheduleName.length("점심")).isEqualTo(2);
		assertThat(ScheduleName.length("${오늘날짜} 오점왓?!")).isEqualTo(16);
		assertThat(ScheduleName.length("${오늘날짜}${오늘날짜}")).isEqualTo(20);
		assertThat(ScheduleName.length("${오늘 날짜}")).isEqualTo("${오늘 날짜}".length());
		// 토큰 7개: 글자 그대로는 49자(@Size 통과)지만 제목은 70자
		String seven = ScheduleName.TODAY.repeat(7);
		assertThat(seven.length()).isEqualTo(49);
		assertThat(ScheduleName.length(seven)).isEqualTo(70);
	}
}
