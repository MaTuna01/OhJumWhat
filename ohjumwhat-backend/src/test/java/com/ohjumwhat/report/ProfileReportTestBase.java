package com.ohjumwhat.report;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 사람 신고 테스트 공통: 개발팀(이영희가 만들고 김철수·박민수·관리자가 들어왔다)과 최준호 혼자인 디자인팀. 김철수(신고되는 사람)는
 * 별명·한줄 소개·좋아하는 음식·상세 프로필을 모두 채웠다.
 */
abstract class ProfileReportTestBase extends IntegrationTest {

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected OrganizationService organizationService;

	@Autowired
	protected InviteService inviteService;

	@Autowired
	protected ProfileReportRepository reportRepository;

	protected User admin;

	protected User kim;

	protected User lee;

	protected User park;

	protected User choi;

	@BeforeEach
	void setUpProfileReportBase() {
		clock.set(2026, 10, 7, 11, 0); // 수요일 오전 11:00 (한국 시간) = 2026-10-07T02:00:00Z
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", "https://lh3.googleusercontent.com/kim"));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		choi = userRepository.save(new User("sub-choi", "choi@example.com", "최준호", null));
		OrganizationResponse dev = organizationService.create(lee.getId(), "개발팀");
		inviteService.join(dev.inviteToken(), kim.getId());
		inviteService.join(dev.inviteToken(), park.getId());
		inviteService.join(dev.inviteToken(), admin.getId());
		organizationService.create(choi.getId(), "디자인팀");
		jdbcTemplate.update("""
				update users set nickname = '철수', bio = '안녕하세요', food_tags = '{라멘,김밥}', mbti = 'ENFP',
					personal_color = 'SPRING_WARM', hobbies = '{등산,독서}', age = 30, job_title = '대리'
				where id = ?""", kim.getId());
	}

	protected ResultActions report(User by, Long targetId, String json) throws Exception {
		return mockMvc.perform(post("/api/users/{userId}/report", targetId).with(loginAs(by))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** 신고하고(204여야 한다) 그 처리 전 신고의 ID를 준다. */
	protected long reportOk(User by, User target, String reason) throws Exception {
		report(by, target.getId(), "{\"reason\": \"" + reason + "\"}").andExpect(status().isNoContent());
		return openReportId(by, target);
	}

	protected long openReportId(User reporter, User target) {
		return jdbcTemplate.queryForObject(
				"select id from profile_reports where reporter_id = ? and target_id = ? and resolved_at is null",
				Long.class, reporter.getId(), target.getId());
	}

	protected int reportCount() {
		return jdbcTemplate.queryForObject("select count(*) from profile_reports", Integer.class);
	}

	protected ResultActions adminReports(String status) throws Exception {
		return mockMvc.perform(get("/api/admin/profile-reports").param("status", status).with(loginAs(admin)));
	}

	protected ResultActions dismiss(User by, long reportId) throws Exception {
		return mockMvc.perform(post("/api/admin/profile-reports/{reportId}/dismiss", reportId).with(loginAs(by))
			.with(xsrf()));
	}

	/** 관리자 API로 제재를 걸고(200이어야 한다) 제재 ID를 준다. */
	protected long sanction(User target, String json) throws Exception {
		String body = mockMvc
			.perform(post("/api/admin/users/{userId}/sanctions", target.getId()).with(loginAs(admin))
				.with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	protected ResultActions lift(long sanctionId) throws Exception {
		return mockMvc.perform(post("/api/admin/sanctions/{sanctionId}/lift", sanctionId).with(loginAs(admin))
			.with(xsrf()));
	}

	/** 관리자 강제 탈퇴(204여야 한다) */
	protected void withdraw(User target) throws Exception {
		mockMvc.perform(delete("/api/admin/users/{userId}", target.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
	}

	protected ResultActions alerts(User user) throws Exception {
		return mockMvc.perform(get("/api/sanctions/alerts").with(loginAs(user)));
	}

	protected ResultActions seen(User user, String idsJson) throws Exception {
		return mockMvc.perform(post("/api/profile-reports/results/seen").with(loginAs(user))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\": " + idsJson + "}"));
	}
}
