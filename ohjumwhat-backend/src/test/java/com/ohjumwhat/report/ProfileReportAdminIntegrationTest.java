package com.ohjumwhat.report;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.sanction.ProfileReset;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.user.User;

/** 관리자 콘솔의 사람 신고: 목록, 문제 없음, 제재·강제 탈퇴와 함께 처리, 개요·회원 상세의 수 */
@RecordApplicationEvents
class ProfileReportAdminIntegrationTest extends ProfileReportTestBase {

	@Autowired
	ApplicationEvents events;

	@Test
	void 목록은_처리_전과_전체로_나누고_신고한_사람과_신고된_사람을_보여준다() throws Exception {
		long open = reportOk(lee, kim, "ABUSE");
		long dismissed = reportOk(park, kim, "SPAM");
		clock.set(2026, 10, 7, 12, 0);
		dismiss(admin, dismissed).andExpect(status().isOk());

		adminReports("open").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(open))
			.andExpect(jsonPath("$[0].reportedAt").value("2026-10-07T02:00:00Z"))
			.andExpect(jsonPath("$[0].reason").value("ABUSE"))
			.andExpect(jsonPath("$[0].detail").value(nullValue()))
			.andExpect(jsonPath("$[0].resolution").value(nullValue()))
			.andExpect(jsonPath("$[0].resolvedAt").value(nullValue()))
			.andExpect(jsonPath("$[0].resolvedByName").value(nullValue()))
			.andExpect(jsonPath("$[0].targetId").value(kim.getId()))
			.andExpect(jsonPath("$[0].targetName").value("철수"))
			.andExpect(jsonPath("$[0].targetEmail").value("kim@example.com"))
			.andExpect(jsonPath("$[0].targetProfileImageUrl").value("https://lh3.googleusercontent.com/kim"))
			.andExpect(jsonPath("$[0].targetAdmin").value(false))
			.andExpect(jsonPath("$[0].snapshot.name").value("철수"))
			.andExpect(jsonPath("$[0].current.name").value("철수"))
			.andExpect(jsonPath("$[0].reporterId").value(lee.getId()))
			.andExpect(jsonPath("$[0].reporterName").value("이영희"))
			.andExpect(jsonPath("$[0].reporterEmail").value("lee@example.com"))
			.andExpect(jsonPath("$[0].result").value(nullValue()));
		// status를 빼면 처리 전만
		mockMvc.perform(get("/api/admin/profile-reports").with(loginAs(admin)))
			.andExpect(jsonPath("$[*].id", contains((int) open)));
		adminReports("all").andExpect(jsonPath("$[*].id", contains((int) dismissed, (int) open)))
			.andExpect(jsonPath("$[0].resolution").value("DISMISSED"))
			.andExpect(jsonPath("$[0].resolvedAt").value("2026-10-07T03:00:00Z"))
			.andExpect(jsonPath("$[0].resolvedByName").value("관리자"))
			.andExpect(jsonPath("$[0].result").value(nullValue()));
	}

	@Test
	void 관리자만_볼_수_있다() throws Exception {
		long reportId = reportOk(lee, kim, "ABUSE");

		mockMvc.perform(get("/api/admin/profile-reports").with(loginAs(lee))).andExpect(status().isForbidden());
		dismiss(lee, reportId).andExpect(status().isForbidden());
	}

	@Test
	void 문제_없음은_한_번만_처리하고_신고한_사람에게_알린다() throws Exception {
		long reportId = reportOk(lee, kim, "ABUSE");
		clock.set(2026, 10, 7, 12, 0);

		dismiss(admin, reportId).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(reportId))
			.andExpect(jsonPath("$.resolution").value("DISMISSED"))
			.andExpect(jsonPath("$.resolvedAt").value("2026-10-07T03:00:00Z"))
			.andExpect(jsonPath("$.resolvedByName").value("관리자"))
			.andExpect(jsonPath("$.result").value(nullValue()));
		assertThat(events.stream(ProfileReportResolvedEvent.class))
			.containsExactly(new ProfileReportResolvedEvent(reportId, lee.getId()));

		// 같은 결과로 다시 처리하면 그대로다(처음 처리한 시각, 알림 없음).
		clock.set(2026, 10, 7, 13, 0);
		dismiss(admin, reportId).andExpect(status().isOk())
			.andExpect(jsonPath("$.resolvedAt").value("2026-10-07T03:00:00Z"));
		assertThat(events.stream(ProfileReportResolvedEvent.class)).hasSize(1);

		dismiss(admin, 999999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("신고를 찾을 수 없어요."));
	}

	@Test
	void 제재하면_그_사람에_대한_처리_전_신고가_모두_조치함이_되고_결과를_베껴_둔다() throws Exception {
		long byLee = reportOk(lee, kim, "ABUSE");
		long byPark = reportOk(park, kim, "PROFILE");
		long dismissed = reportOk(admin, kim, "ETC");
		dismiss(admin, dismissed).andExpect(status().isOk());
		long other = reportOk(lee, park, "SPAM");
		clock.set(2026, 10, 7, 12, 0);

		// 김철수는 올린 사진이 없다: 비어 있는 항목의 초기화는 기록에서 빠지고 결과에도 없다.
		long sanctionId = sanction(kim, """
				{"restrictions": ["LETTER", "CHAT"], "resets": ["PHOTO", "NICKNAME"], "days": 7, "reason": "ABUSE",
					"note": "관리자 메모"}""");

		for (long reportId : new long[] { byLee, byPark }) {
			ProfileReport report = reportRepository.findById(reportId).orElseThrow();
			assertThat(report.getResolution()).isEqualTo(ProfileReportResolution.ACTIONED);
			assertThat(report.getResolvedAt()).isEqualTo(Instant.parse("2026-10-07T03:00:00Z"));
			assertThat(report.getResolvedBy()).isEqualTo(admin.getId());
			assertThat(report.getSanctionId()).isEqualTo(sanctionId);
			assertThat(report.getResultRestrictions()).containsExactly(Restriction.CHAT, Restriction.LETTER);
			assertThat(report.getResultResets()).containsExactly(ProfileReset.NICKNAME);
			assertThat(report.getResultEndsAt()).isEqualTo(Instant.parse("2026-10-14T03:00:00Z"));
		}
		assertThat(reportRepository.findById(dismissed).orElseThrow().getResolution())
			.isEqualTo(ProfileReportResolution.DISMISSED);
		assertThat(reportRepository.findById(other).orElseThrow().getResolution()).isNull();
		assertThat(events.stream(ProfileReportResolvedEvent.class)).containsExactly(
				new ProfileReportResolvedEvent(dismissed, admin.getId()), new ProfileReportResolvedEvent(byLee, lee.getId()),
				new ProfileReportResolvedEvent(byPark, park.getId()));

		adminReports("all").andExpect(jsonPath("$[?(@.id == " + byLee + ")].resolution", contains("ACTIONED")))
			.andExpect(jsonPath("$[?(@.id == " + byLee + ")].result.restrictions[*]", contains("CHAT", "LETTER")))
			.andExpect(jsonPath("$[?(@.id == " + byLee + ")].result.resets[*]", contains("NICKNAME")))
			.andExpect(jsonPath("$[?(@.id == " + byLee + ")].result.endsAt", contains("2026-10-14T03:00:00Z")))
			// 별명을 초기화해 지금 이름은 구글 이름이다.
			.andExpect(jsonPath("$[?(@.id == " + byLee + ")].current.name", contains("김철수")))
			.andExpect(jsonPath("$[?(@.id == " + byLee + ")].snapshot.name", contains("철수")));
		// 이미 조치한 신고는 문제 없음으로 바꿀 수 없다.
		dismiss(admin, byLee).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 처리한 신고예요."));
	}

	@Test
	void 경고만_보내도_조치함이다() throws Exception {
		long reportId = reportOk(lee, kim, "ABUSE");

		sanction(kim, "{\"reason\": \"ETC\"}");

		adminReports("all").andExpect(jsonPath("$[0].id").value(reportId))
			.andExpect(jsonPath("$[0].resolution").value("ACTIONED"))
			.andExpect(jsonPath("$[0].result.restrictions", empty()))
			.andExpect(jsonPath("$[0].result.resets", empty()))
			.andExpect(jsonPath("$[0].result.endsAt").value(nullValue()));
	}

	@Test
	void 해제하거나_신고된_사람이_탈퇴해도_결과_사본은_그대로다() throws Exception {
		long reportId = reportOk(lee, kim, "ABUSE");
		long sanctionId = sanction(kim, "{\"restrictions\": [\"SUSPEND\"], \"reason\": \"ABUSE\"}");
		clock.set(2026, 10, 7, 12, 0);
		lift(sanctionId).andExpect(status().isOk());

		adminReports("all").andExpect(jsonPath("$[0].result.restrictions", contains("SUSPEND")))
			.andExpect(jsonPath("$[0].result.endsAt").value(nullValue()));

		withdraw(kim);

		ProfileReport report = reportRepository.findById(reportId).orElseThrow();
		assertThat(report.getTargetId()).isNull();
		assertThat(report.getSanctionId()).isNull();
		assertThat(report.getResolution()).isEqualTo(ProfileReportResolution.ACTIONED);
		adminReports("all").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].resolution").value("ACTIONED"))
			.andExpect(jsonPath("$[0].result.restrictions", contains("SUSPEND")))
			.andExpect(jsonPath("$[0].targetId").value(nullValue()))
			.andExpect(jsonPath("$[0].targetName").value(nullValue()))
			.andExpect(jsonPath("$[0].targetEmail").value(nullValue()))
			.andExpect(jsonPath("$[0].targetProfileImageUrl").value(nullValue()))
			.andExpect(jsonPath("$[0].targetAdmin").value(false))
			.andExpect(jsonPath("$[0].current").value(nullValue()))
			.andExpect(jsonPath("$[0].snapshot.name").value("철수"));
		alerts(lee).andExpect(jsonPath("$.reportResults[0].id").value(reportId))
			.andExpect(jsonPath("$.reportResults[0].targetName").value("철수"))
			.andExpect(jsonPath("$.reportResults[0].resolution").value("ACTIONED"))
			.andExpect(jsonPath("$.reportResults[0].restrictions", contains("SUSPEND")));
	}

	@Test
	void 강제_탈퇴하면_그_사람에_대한_신고는_탈퇴_처리되고_그_사람이_한_신고는_남는다() throws Exception {
		long byLee = reportOk(lee, kim, "ABUSE");
		long byPark = reportOk(park, kim, "SPAM");
		long byKim = reportOk(kim, lee, "PROFILE");
		clock.set(2026, 10, 7, 12, 0);

		withdraw(kim);

		for (long reportId : new long[] { byLee, byPark }) {
			ProfileReport report = reportRepository.findById(reportId).orElseThrow();
			assertThat(report.getResolution()).isEqualTo(ProfileReportResolution.WITHDRAWN);
			assertThat(report.getResolvedAt()).isEqualTo(Instant.parse("2026-10-07T03:00:00Z"));
			assertThat(report.getResolvedBy()).isEqualTo(admin.getId());
			assertThat(report.getTargetId()).isNull();
			assertThat(report.getResultRestrictions()).isEmpty();
		}
		assertThat(events.stream(ProfileReportResolvedEvent.class)).containsExactly(
				new ProfileReportResolvedEvent(byLee, lee.getId()), new ProfileReportResolvedEvent(byPark, park.getId()));

		adminReports("open").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(byKim))
			.andExpect(jsonPath("$[0].reporterId").value(nullValue()))
			.andExpect(jsonPath("$[0].reporterName").value(nullValue()))
			.andExpect(jsonPath("$[0].reporterEmail").value(nullValue()))
			.andExpect(jsonPath("$[0].targetId").value(lee.getId()));
		adminReports("all").andExpect(jsonPath("$[?(@.id == " + byLee + ")].resolution", contains("WITHDRAWN")))
			.andExpect(jsonPath("$[?(@.id == " + byLee + ")].result", contains(nullValue())));

		// 신고한 사람이 탈퇴한 신고도 처리할 수 있다(알릴 사람은 없다).
		dismiss(admin, byKim).andExpect(status().isOk()).andExpect(jsonPath("$.resolution").value("DISMISSED"));
		assertThat(events.stream(ProfileReportResolvedEvent.class))
			.contains(new ProfileReportResolvedEvent(byKim, null));
	}

	@Test
	void 개요와_회원_상세에_처리_전_신고_수가_보인다() throws Exception {
		reportOk(lee, kim, "ABUSE");
		reportOk(park, kim, "ABUSE");
		long dismissed = reportOk(lee, park, "SPAM");

		stats().andExpect(jsonPath("$.openProfileReportCount").value(3))
			.andExpect(jsonPath("$.openReportCount").value(3))
			.andExpect(jsonPath("$.openLetterReportCount").value(0))
			.andExpect(jsonPath("$.openGuestbookReportCount").value(0));
		userDetail(kim).andExpect(jsonPath("$.openProfileReportCount").value(2));
		userDetail(park).andExpect(jsonPath("$.openProfileReportCount").value(1));

		dismiss(admin, dismissed).andExpect(status().isOk());
		sanction(kim, "{\"reason\": \"ETC\"}");

		stats().andExpect(jsonPath("$.openProfileReportCount").value(0)).andExpect(jsonPath("$.openReportCount").value(0));
		userDetail(kim).andExpect(jsonPath("$.openProfileReportCount").value(0));
		userDetail(park).andExpect(jsonPath("$.openProfileReportCount").value(0));
	}

	private ResultActions stats() throws Exception {
		return mockMvc.perform(get("/api/admin/stats").with(loginAs(admin))).andExpect(status().isOk());
	}

	private ResultActions userDetail(User user) throws Exception {
		return mockMvc.perform(get("/api/admin/users/{userId}", user.getId()).with(loginAs(admin)))
			.andExpect(status().isOk());
	}
}
