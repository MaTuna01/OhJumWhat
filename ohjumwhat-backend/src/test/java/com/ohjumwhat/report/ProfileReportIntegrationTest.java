package com.ohjumwhat.report;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 사람 신고(사용자): 확인 순서, 처리 전 중복, 다시 신고, 신고할 때의 프로필 사본 */
class ProfileReportIntegrationTest extends ProfileReportTestBase {

	@Test
	void 나_자신_같은_조직이_아닌_사람_없는_회원은_신고할_수_없다() throws Exception {
		report(lee, lee.getId(), "{\"reason\": \"ABUSE\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("나 자신은 신고할 수 없어요."));
		// 나 자신이 사유보다 먼저다.
		report(lee, lee.getId(), "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("나 자신은 신고할 수 없어요."));
		report(lee, choi.getId(), "{\"reason\": \"ABUSE\"}").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("사람을 찾을 수 없어요."));
		report(lee, 999999L, "{\"reason\": \"ABUSE\"}").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("사람을 찾을 수 없어요."));
		// 없는 사람이 사유보다 먼저다(있는지 알리지 않는다).
		report(lee, choi.getId(), "{}").andExpect(status().isNotFound());

		assertThat(reportCount()).isZero();
	}

	@Test
	void 사유는_꼭_고르고_설명은_한_줄_100자다() throws Exception {
		report(lee, kim.getId(), "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("무엇이 문제인지 골라 주세요."));
		report(lee, kim.getId(), "{\"reason\": \"RUDE\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("무엇이 문제인지 골라 주세요."));
		report(lee, kim.getId(), "{\"reason\": \"ABUSE\", \"detail\": \"" + "가".repeat(101) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("100자 이하로 입력해 주세요."));
		report(lee, kim.getId(), "{\"reason\": \"ABUSE\", \"detail\": \"첫 줄\\n둘째 줄\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("쓸 수 없는 문자가 있어요."));
		assertThat(reportCount()).isZero();

		report(lee, kim.getId(), "{\"reason\": \"ABUSE\", \"detail\": \"  " + "가".repeat(100) + "  \"}")
			.andExpect(status().isNoContent());
		report(park, kim.getId(), "{\"reason\": \"SPAM\", \"detail\": \"   \"}").andExpect(status().isNoContent());
		report(admin, kim.getId(), "{\"reason\": \"ETC\"}").andExpect(status().isNoContent());

		ProfileReport byLee = reportRepository.findById(openReportId(lee, kim)).orElseThrow();
		assertThat(byLee.getReason()).hasToString("ABUSE");
		assertThat(byLee.getDetail()).isEqualTo("가".repeat(100));
		assertThat(byLee.getCreatedAt()).isEqualTo(Instant.parse("2026-10-07T02:00:00Z"));
		assertThat(byLee.getResolution()).isNull();
		assertThat(reportRepository.findById(openReportId(park, kim)).orElseThrow().getDetail()).isNull();
		assertThat(reportRepository.findById(openReportId(admin, kim)).orElseThrow().getDetail()).isNull();
	}

	@Test
	void 처리_전_신고가_있으면_그대로고_처리된_뒤에는_다시_신고할_수_있다() throws Exception {
		report(lee, kim.getId(), "{\"reason\": \"ABUSE\", \"detail\": \"처음\"}").andExpect(status().isNoContent());
		clock.set(2026, 10, 7, 11, 30);
		report(lee, kim.getId(), "{\"reason\": \"SPAM\", \"detail\": \"다시\"}").andExpect(status().isNoContent());

		assertThat(reportCount()).isEqualTo(1);
		long first = openReportId(lee, kim);
		ProfileReport report = reportRepository.findById(first).orElseThrow();
		assertThat(report.getReason()).hasToString("ABUSE");
		assertThat(report.getDetail()).isEqualTo("처음");

		dismiss(admin, first).andExpect(status().isOk());
		report(lee, kim.getId(), "{\"reason\": \"SPAM\", \"detail\": \"다시\"}").andExpect(status().isNoContent());

		assertThat(reportCount()).isEqualTo(2);
		long second = openReportId(lee, kim);
		assertThat(second).isNotEqualTo(first);
		assertThat(reportRepository.findById(second).orElseThrow().getDetail()).isEqualTo("다시");
	}

	@Test
	void 관리자도_오류_없이_신고를_받는다() throws Exception {
		report(lee, admin.getId(), "{\"reason\": \"ABUSE\"}").andExpect(status().isNoContent());

		adminReports("open").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].targetId").value(admin.getId()))
			.andExpect(jsonPath("$[0].targetAdmin").value(true));
	}

	@Test
	void 이용이_제한돼도_신고할_수_있다() throws Exception {
		sanction(lee, "{\"restrictions\": [\"SUSPEND\"], \"reason\": \"ABUSE\"}");

		report(lee, kim.getId(), "{\"reason\": \"ABUSE\"}").andExpect(status().isNoContent());
		assertThat(reportCount()).isEqualTo(1);
	}

	@Test
	void 신고할_때의_프로필을_남기고_나중에_바꿔도_그대로다() throws Exception {
		report(lee, kim.getId(), "{\"reason\": \"PROFILE\"}").andExpect(status().isNoContent());
		// 상세 프로필을 채우지 않은 사람은 취미·직급이 비어 있다.
		report(lee, park.getId(), "{\"reason\": \"PROFILE\"}").andExpect(status().isNoContent());

		mockMvc.perform(put("/api/me/nickname").with(loginAs(kim))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"새 별명\"}")).andExpect(status().isOk());
		mockMvc.perform(put("/api/me/profile").with(loginAs(kim))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bio\": null, \"foodTags\": [\"피자\"]}")).andExpect(status().isOk());

		adminReports("open").andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[1].targetId").value(kim.getId()))
			.andExpect(jsonPath("$[1].snapshot.name").value("철수"))
			.andExpect(jsonPath("$[1].snapshot.bio").value("안녕하세요"))
			.andExpect(jsonPath("$[1].snapshot.foodTags", contains("라멘", "김밥")))
			.andExpect(jsonPath("$[1].snapshot.hobbies", contains("등산", "독서")))
			.andExpect(jsonPath("$[1].snapshot.jobTitle").value("대리"))
			.andExpect(jsonPath("$[1].targetName").value("새 별명"))
			.andExpect(jsonPath("$[1].current.name").value("새 별명"))
			.andExpect(jsonPath("$[1].current.bio").value(nullValue()))
			.andExpect(jsonPath("$[1].current.foodTags", contains("피자")))
			.andExpect(jsonPath("$[1].current.hobbies", contains("등산", "독서")))
			.andExpect(jsonPath("$[1].current.jobTitle").value("대리"))
			.andExpect(jsonPath("$[0].targetId").value(park.getId()))
			.andExpect(jsonPath("$[0].snapshot.name").value("박민수"))
			.andExpect(jsonPath("$[0].snapshot.bio").value(nullValue()))
			.andExpect(jsonPath("$[0].snapshot.foodTags", empty()))
			.andExpect(jsonPath("$[0].snapshot.hobbies", empty()))
			.andExpect(jsonPath("$[0].snapshot.jobTitle").value(nullValue()));
	}
}
