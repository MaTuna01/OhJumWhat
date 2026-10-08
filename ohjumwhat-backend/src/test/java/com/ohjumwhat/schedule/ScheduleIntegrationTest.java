package com.ohjumwhat.schedule;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class ScheduleIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ScheduledPollOpener opener;

	User kim;

	Long orgId;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		orgId = organizationService.create(kim.getId(), "개발팀").id();
	}

	@Test
	void 규칙을_추가하면_오픈_시간순으로_보인다() throws Exception {
		create(kim, "저녁", 16, "17:00", "17:40").andExpect(status().isCreated());
		create(kim, "  점심 ", 31, "11:00", "11:50")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("점심"))
			.andExpect(jsonPath("$.daysOfWeek").value(31))
			.andExpect(jsonPath("$.openTime").value("11:00"))
			.andExpect(jsonPath("$.closeTime").value("11:50"));

		mockMvc.perform(get("/api/orgs/" + orgId + "/schedules").with(loginAs(kim)))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].name").value("점심"))
			.andExpect(jsonPath("$[1].name").value("저녁"));
	}

	@Test
	void 마감이_오픈보다_이르거나_요일이_없으면_400() throws Exception {
		create(kim, "점심", 31, "11:50", "11:00")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("마감 시간은 오픈 시간보다 늦어야 해요."));
		create(kim, "점심", 31, "11:00", "11:00").andExpect(status().isBadRequest());
		create(kim, "점심", 0, "11:00", "11:50")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요일을 하나 이상 골라 주세요."));
		create(kim, "점심", 128, "11:00", "11:50").andExpect(status().isBadRequest());
		create(kim, "점심", 31, "9:00", "11:50").andExpect(status().isBadRequest());
	}

	@Test
	void 이름_길이는_날짜_토큰을_10자로_센다() throws Exception {
		// 토큰(7자) + 41자 = 48자지만 제목이 되면 51자
		create(kim, "${오늘날짜}" + "가".repeat(41), 31, "11:00", "11:50")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("규칙 이름은 50자 이하로 입력해 주세요. 오늘 날짜는 10자로 세요."));
		create(kim, "${오늘날짜}" + "가".repeat(40), 31, "11:00", "11:50")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("${오늘날짜}" + "가".repeat(40)));
		Long id = id(create(kim, "${오늘날짜} 점심", 31, "11:00", "11:50"));
		mockMvc.perform(put("/api/orgs/" + orgId + "/schedules/" + id).with(loginAs(kim)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(body("${오늘날짜}" + "가".repeat(41), 31, "11:00", "11:50")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 규칙을_수정한다() throws Exception {
		Long id = id(create(kim, "점심", 31, "11:00", "11:50"));

		mockMvc.perform(put("/api/orgs/" + orgId + "/schedules/" + id).with(loginAs(kim)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(body("점심 (주 3회)", 1 + 4 + 16, "11:30", "12:10")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("점심 (주 3회)"))
			.andExpect(jsonPath("$.daysOfWeek").value(21))
			.andExpect(jsonPath("$.openTime").value("11:30"));
	}

	@Test
	void 규칙을_지워도_이미_열린_투표는_남는다() throws Exception {
		clock.set(2026, 9, 30, 11, 0);
		Long id = id(create(kim, "점심", 31, "11:00", "11:50"));
		assertThat(opener.openDuePolls()).isEqualTo(1);

		mockMvc.perform(delete("/api/orgs/" + orgId + "/schedules/" + id).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());

		assertThat(jdbcTemplate.queryForObject("select count(*) from polls where schedule_id is null", Long.class))
			.isEqualTo(1);
		mockMvc.perform(get("/api/orgs/" + orgId + "/schedules").with(loginAs(kim))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 멤버가_아니거나_다른_조직의_규칙이면_404() throws Exception {
		Long id = id(create(kim, "점심", 31, "11:00", "11:50"));
		User stranger = userRepository.save(new User("sub-x", "x@example.com", "외부인", null));
		Long otherOrg = organizationService.create(stranger.getId(), "다른팀").id();

		mockMvc.perform(get("/api/orgs/" + orgId + "/schedules").with(loginAs(stranger))).andExpect(status().isNotFound());
		create(stranger, "점심", 31, "11:00", "11:50").andExpect(status().isNotFound());
		mockMvc.perform(put("/api/orgs/" + otherOrg + "/schedules/" + id).with(loginAs(stranger)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(body("탈취", 31, "11:00", "11:50")))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/orgs/" + otherOrg + "/schedules/" + id).with(loginAs(stranger)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	private ResultActions create(User user, String name, int days, String open, String close) throws Exception {
		return mockMvc.perform(post("/api/orgs/" + orgId + "/schedules").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body(name, days, open, close)));
	}

	private static String body(String name, int days, String open, String close) {
		return "{\"name\": \"%s\", \"daysOfWeek\": %d, \"openTime\": \"%s\", \"closeTime\": \"%s\"}"
			.formatted(name, days, open, close);
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}
}
