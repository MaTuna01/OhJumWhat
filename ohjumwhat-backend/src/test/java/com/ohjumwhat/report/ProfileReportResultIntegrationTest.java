package com.ohjumwhat.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.FakePushSenderConfiguration.Sent;

/** 신고한 사람에게 알리는 결과: 다음 화면의 결과 창(alerts의 reportResults, seen)과 커밋 뒤 푸시 */
class ProfileReportResultIntegrationTest extends ProfileReportTestBase {

	static final String LEE_PHONE = "fid_lee_phone_000001";

	@Test
	void 처리된_내_신고만_처리한_순서대로_준다() throws Exception {
		long leeOnKim = reportOk(lee, kim, "ABUSE");
		long leeOnPark = reportOk(lee, park, "SPAM");
		long parkOnKim = reportOk(park, kim, "PROFILE");
		long leeOnAdmin = reportOk(lee, admin, "ETC");
		clock.set(2026, 10, 7, 12, 0);
		dismiss(admin, leeOnPark).andExpect(status().isOk());
		clock.set(2026, 10, 7, 13, 0);
		sanction(kim, "{\"restrictions\": [\"CHAT\"], \"days\": 3, \"reason\": \"ABUSE\", \"note\": \"관리자 메모\"}");

		alerts(lee).andExpect(status().isOk())
			.andExpect(jsonPath("$.sanctions", empty()))
			.andExpect(jsonPath("$.reportResults[*].id", contains((int) leeOnPark, (int) leeOnKim)))
			.andExpect(jsonPath("$.reportResults[0].targetName").value("박민수"))
			.andExpect(jsonPath("$.reportResults[0].reportedAt").value("2026-10-07T02:00:00Z"))
			.andExpect(jsonPath("$.reportResults[0].reason").value("SPAM"))
			.andExpect(jsonPath("$.reportResults[0].resolution").value("DISMISSED"))
			.andExpect(jsonPath("$.reportResults[0].resolvedAt").value("2026-10-07T03:00:00Z"))
			.andExpect(jsonPath("$.reportResults[0].restrictions", empty()))
			.andExpect(jsonPath("$.reportResults[0].resets", empty()))
			.andExpect(jsonPath("$.reportResults[0].endsAt").value(nullValue()))
			.andExpect(jsonPath("$.reportResults[1].targetName").value("철수"))
			.andExpect(jsonPath("$.reportResults[1].reason").value("ABUSE"))
			.andExpect(jsonPath("$.reportResults[1].resolution").value("ACTIONED"))
			.andExpect(jsonPath("$.reportResults[1].resolvedAt").value("2026-10-07T04:00:00Z"))
			.andExpect(jsonPath("$.reportResults[1].restrictions", contains("CHAT")))
			.andExpect(jsonPath("$.reportResults[1].resets", empty()))
			.andExpect(jsonPath("$.reportResults[1].endsAt").value("2026-10-10T04:00:00Z"))
			// 관리자 설명과 신고 설명은 보여주지 않는다.
			.andExpect(jsonPath("$.reportResults[1].note").doesNotExist())
			.andExpect(jsonPath("$.reportResults[1].detail").doesNotExist());
		alerts(park).andExpect(jsonPath("$.reportResults[*].id", contains((int) parkOnKim)));
		// 신고된 사람에게는 신고 결과가 가지 않는다(제재 안내만 간다).
		alerts(kim).andExpect(jsonPath("$.sanctions", hasSize(1))).andExpect(jsonPath("$.reportResults", empty()));
		// 처리 전 신고는 아직 없다.
		assertThat(reportRepository.findById(leeOnAdmin).orElseThrow().getResolution()).isNull();
	}

	@Test
	void 본_결과는_다시_주지_않고_남의_것과_처리_전_신고는_표시하지_않는다() throws Exception {
		long leeOnKim = reportOk(lee, kim, "ABUSE");
		long leeOnPark = reportOk(lee, park, "ABUSE");
		long parkOnKim = reportOk(park, kim, "ABUSE");
		long leeOnAdmin = reportOk(lee, admin, "ABUSE");
		sanction(kim, "{\"reason\": \"ETC\"}");
		dismiss(admin, leeOnPark).andExpect(status().isOk());

		clock.set(2026, 10, 7, 11, 30);
		seen(lee, "[" + leeOnKim + ", " + parkOnKim + ", " + leeOnAdmin + ", 9999]").andExpect(status().isNoContent());

		alerts(lee).andExpect(jsonPath("$.reportResults[*].id", contains((int) leeOnPark)));
		alerts(park).andExpect(jsonPath("$.reportResults[*].id", contains((int) parkOnKim)));
		assertThat(reportRepository.findById(leeOnKim).orElseThrow().getResultSeenAt())
			.isEqualTo(Instant.parse("2026-10-07T02:30:00Z"));
		assertThat(reportRepository.findById(parkOnKim).orElseThrow().getResultSeenAt()).isNull();
		// 처리 전 신고는 봤다고 표시하지 않는다(처리되면 결과 창에 나와야 한다).
		assertThat(reportRepository.findById(leeOnAdmin).orElseThrow().getResultSeenAt()).isNull();
		dismiss(admin, leeOnAdmin).andExpect(status().isOk());
		alerts(lee).andExpect(jsonPath("$.reportResults[*].id", contains((int) leeOnPark, (int) leeOnAdmin)));

		// 이미 본 것은 처음 본 시각 그대로 둔다.
		clock.set(2026, 10, 7, 12, 0);
		seen(lee, "[" + leeOnKim + ", " + leeOnPark + ", " + leeOnAdmin + "]").andExpect(status().isNoContent());
		assertThat(reportRepository.findById(leeOnKim).orElseThrow().getResultSeenAt())
			.isEqualTo(Instant.parse("2026-10-07T02:30:00Z"));
		alerts(lee).andExpect(jsonPath("$.reportResults", empty()));
	}

	@Test
	void 확인한_결과가_올바르지_않으면_400이다() throws Exception {
		String fiftyOne = LongStream.rangeClosed(1, 51).mapToObj(Long::toString).collect(Collectors.joining(",", "[", "]"));
		String fifty = LongStream.rangeClosed(1, 50).mapToObj(Long::toString).collect(Collectors.joining(",", "[", "]"));

		seen(lee, "[]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("확인한 안내가 올바르지 않아요."));
		seen(lee, "null").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("확인한 안내가 올바르지 않아요."));
		seen(lee, "[1, null]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("확인한 안내가 올바르지 않아요."));
		seen(lee, fiftyOne).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("확인한 안내가 올바르지 않아요."));
		seen(lee, fifty).andExpect(status().isNoContent());
	}

	@Test
	void 처리되면_신고한_사람에게_내용_없이_알린다() throws Exception {
		registerPushDevice(lee, loginSession(lee), LEE_PHONE).andExpect(status().isOk());
		long leeOnKim = reportOk(lee, kim, "ABUSE");
		long leeOnPark = reportOk(lee, park, "ABUSE");
		reportOk(lee, admin, "ABUSE");
		pushDispatcher.drain();
		// 신고만으로는 아무에게도 알리지 않는다.
		assertThat(pushSender.sent()).isEmpty();

		dismiss(admin, leeOnPark).andExpect(status().isOk());
		sanction(kim, "{\"restrictions\": [\"CHAT\"], \"days\": 7, \"reason\": \"ABUSE\"}");
		pushDispatcher.drain();

		assertThat(pushSender.sent()).hasSize(2);
		Sent sent = pushSender.sent().get(0);
		assertThat(sent.fids()).containsExactly(LEE_PHONE);
		assertThat(sent.message().data()).isEqualTo(Map.of("kind", "REPORT_RESULT", "title", "신고 처리 결과가 도착했어요",
				"url", "/me", "tag", "ohjumwhat-report-result"));
		assertThat(pushSender.sent().get(1).message().title()).isEqualTo("신고 처리 결과가 도착했어요");
		assertThat(reportRepository.findById(leeOnKim).orElseThrow().getResolution())
			.isEqualTo(ProfileReportResolution.ACTIONED);
	}

	@Test
	void 강제_탈퇴로_처리돼도_신고한_사람에게_알린다() throws Exception {
		registerPushDevice(lee, loginSession(lee), LEE_PHONE).andExpect(status().isOk());
		reportOk(lee, kim, "ABUSE");

		withdraw(kim);
		pushDispatcher.drain();

		assertThat(pushSender.sent()).extracting(sent -> sent.message().title()).containsExactly("신고 처리 결과가 도착했어요");
		alerts(lee).andExpect(jsonPath("$.reportResults[0].resolution").value("WITHDRAWN"))
			.andExpect(jsonPath("$.reportResults[0].targetName").value("철수"))
			.andExpect(jsonPath("$.reportResults[0].restrictions", empty()));
	}

	@Test
	void 신고한_사람이_탈퇴했으면_알리지_않는다() throws Exception {
		registerPushDevice(lee, loginSession(lee), LEE_PHONE).andExpect(status().isOk());
		long reportId = reportOk(lee, kim, "ABUSE");
		withdraw(lee);
		pushDispatcher.drain();

		dismiss(admin, reportId).andExpect(status().isOk());

		assertThat(pushDispatcher.size()).isZero();
		pushDispatcher.drain();
		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void 알리기_전에_신고가_없어졌으면_알리지_않는다() throws Exception {
		registerPushDevice(lee, loginSession(lee), LEE_PHONE).andExpect(status().isOk());
		long reportId = reportOk(lee, kim, "ABUSE");
		dismiss(admin, reportId).andExpect(status().isOk());
		jdbcTemplate.update("delete from profile_reports where id = ?", reportId);
		pushDispatcher.drain();

		assertThat(pushSender.sent()).isEmpty();
	}
}
