package com.ohjumwhat.organization;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.common.TimeConfig;
import com.ohjumwhat.menu.MenuOption;
import com.ohjumwhat.menu.MenuOptionRepository;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.schedule.PollSchedule;
import com.ohjumwhat.schedule.PollScheduleRepository;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.Vote;
import com.ohjumwhat.vote.VoteRepository;

class OrganizationIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	PollRepository pollRepository;

	@Autowired
	PollScheduleRepository pollScheduleRepository;

	@Autowired
	MenuOptionRepository menuOptionRepository;

	@Autowired
	VoteRepository voteRepository;

	@Test
	void 조직을_만들면_만든_사람이_첫_멤버가_되고_초대_토큰이_발급된다() throws Exception {
		User kim = user("kim");

		mockMvc.perform(post("/api/orgs").with(loginAs(kim)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  개발팀  \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("개발팀"))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.inviteToken").isString());

		mockMvc.perform(get("/api/me/orgs").with(loginAs(kim)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("개발팀"))
			.andExpect(jsonPath("$[0].memberCount").value(1))
			.andExpect(jsonPath("$[0].hasOpenPollToday").value(false));
	}

	@Test
	void 조직_이름이_비어_있으면_400() throws Exception {
		mockMvc.perform(post("/api/orgs").with(loginAs(user("kim"))).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조직 이름을 입력해 주세요."));
	}

	@Test
	void 멤버가_아니면_조직_API는_404() throws Exception {
		Long orgId = createOrg(user("kim"), "개발팀");
		User stranger = user("stranger");

		mockMvc.perform(get("/api/orgs/" + orgId).with(loginAs(stranger))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(stranger)))
			.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/orgs/" + orgId).with(loginAs(stranger)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"탈취\"}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/orgs/" + orgId + "/membership").with(loginAs(stranger)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 조직_홈에_들어가면_최근_들어간_조직이_바뀐다() throws Exception {
		User kim = user("kim");
		Long first = createOrg(kim, "A팀");
		createOrg(kim, "B팀");

		mockMvc.perform(get("/api/orgs/" + first).with(loginAs(kim)))
			.andExpect(jsonPath("$.name").value("A팀"));

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.lastVisitedOrgId").value(first));
	}

	@Test
	void 멤버라면_누구나_조직_이름을_바꿀_수_있다() throws Exception {
		User kim = user("kim");
		User lee = user("lee");
		Long orgId = createOrg(kim, "개발팀");
		join(lee, orgId);

		mockMvc.perform(patch("/api/orgs/" + orgId).with(loginAs(lee)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"플랫폼팀\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("플랫폼팀"));
	}

	@Test
	void 멤버_목록은_가입_순서대로_보여준다() throws Exception {
		User kim = user("kim");
		User lee = user("lee");
		Long orgId = createOrg(kim, "개발팀");
		join(lee, orgId);

		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].name").value("kim"))
			.andExpect(jsonPath("$[1].name").value("lee"));
	}

	@Test
	void 오늘_진행_중인_투표가_있는_조직을_표시한다() throws Exception {
		User kim = user("kim");
		Long withPoll = createOrg(kim, "A팀");
		Long withClosedPoll = createOrg(kim, "B팀");
		Instant now = Instant.now();
		pollRepository.save(Poll.manual(withPoll, kim.getId(), "점심", today(), now.minusSeconds(60),
				now.plus(1, ChronoUnit.HOURS)));
		pollRepository.save(Poll.manual(withClosedPoll, kim.getId(), "점심", today(),
				now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)));

		mockMvc.perform(get("/api/me/orgs").with(loginAs(kim)))
			.andExpect(jsonPath("$[0].id").value(withPoll))
			.andExpect(jsonPath("$[0].hasOpenPollToday").value(true))
			.andExpect(jsonPath("$[1].hasOpenPollToday").value(false));
	}

	@Test
	void 탈퇴하면_진행_중인_투표의_내_응답만_지우고_마감된_기록은_남긴다() throws Exception {
		User kim = user("kim");
		User lee = user("lee");
		Long orgId = createOrg(kim, "개발팀");
		join(lee, orgId);
		Instant now = Instant.now();
		Poll open = pollRepository.save(Poll.manual(orgId, kim.getId(), "오늘 점심", today(), now.minusSeconds(60),
				now.plus(1, ChronoUnit.HOURS)));
		Poll closed = pollRepository.save(Poll.manual(orgId, kim.getId(), "어제 점심", today().minusDays(1),
				now.minus(25, ChronoUnit.HOURS), now.minus(24, ChronoUnit.HOURS)));
		MenuOption openMenu = menuOptionRepository.save(new MenuOption(open.getId(), kim.getId(), "김치찌개"));
		MenuOption closedMenu = menuOptionRepository.save(new MenuOption(closed.getId(), kim.getId(), "국밥"));
		voteRepository.save(new Vote(open.getId(), lee.getId(), openMenu.getId()));
		voteRepository.save(new Vote(closed.getId(), lee.getId(), closedMenu.getId()));
		voteRepository.save(new Vote(open.getId(), kim.getId(), openMenu.getId()));

		mockMvc.perform(delete("/api/orgs/" + orgId + "/membership").with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organizationDeleted").value(false));

		assertThat(voteRepository.findAll())
			.extracting(Vote::getPollId, Vote::getUserId)
			.containsExactlyInAnyOrder(
					tuple(closed.getId(), lee.getId()),
					tuple(open.getId(), kim.getId()));
		mockMvc.perform(get("/api/orgs/" + orgId).with(loginAs(lee))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orgs/" + orgId).with(loginAs(kim))).andExpect(jsonPath("$.memberCount").value(1));
	}

	@Test
	void 마지막_멤버가_탈퇴하면_조직과_하위_데이터를_모두_지운다() throws Exception {
		User kim = user("kim");
		Long orgId = createOrg(kim, "개발팀");
		PollSchedule schedule = pollScheduleRepository.save(
				new PollSchedule(orgId, "점심", 31, LocalTime.of(11, 0), LocalTime.of(11, 50)));
		Instant now = Instant.now();
		Poll poll = pollRepository.save(Poll.scheduled(orgId, schedule.getId(), "점심", today(),
				now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)));
		MenuOption menu = menuOptionRepository.save(new MenuOption(poll.getId(), kim.getId(), "국밥"));
		voteRepository.save(new Vote(poll.getId(), kim.getId(), menu.getId()));

		mockMvc.perform(delete("/api/orgs/" + orgId + "/membership").with(loginAs(kim)).with(xsrf()))
			.andExpect(jsonPath("$.organizationDeleted").value(true));

		for (String table : new String[] { "organizations", "memberships", "poll_schedules", "polls",
				"menu_options", "votes" }) {
			assertThat(jdbcTemplate.queryForObject("select count(*) from " + table, Long.class))
				.as(table).isZero();
		}
		assertThat(userRepository.count()).isEqualTo(1);
	}

	private User user(String name) {
		return userRepository.save(new User("sub-" + name, name + "@example.com", name, null));
	}

	private Long createOrg(User user, String name) throws Exception {
		String body = mockMvc.perform(post("/api/orgs").with(loginAs(user)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"))
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void join(User user, Long orgId) throws Exception {
		String token = jdbcTemplate.queryForObject("select invite_token from organizations where id = ?",
				String.class, orgId);
		mockMvc.perform(post("/api/invites/" + token + "/join").with(loginAs(user)).with(xsrf()))
			.andExpect(status().isOk());
	}

	private static LocalDate today() {
		return LocalDate.now(TimeConfig.KST);
	}
}
