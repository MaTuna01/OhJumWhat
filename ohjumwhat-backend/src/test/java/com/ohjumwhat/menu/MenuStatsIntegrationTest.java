package com.ohjumwhat.menu;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteService;

/**
 * 9/21(월) 점심: 김치찌개(김·이), 돈까스(박), 냉면(아무도 없음) → 마감
 * 9/25(금) 점심: "김치 찌개"(김), 마라탕(아무도 없음) → 마감
 * 9/30(수) 점심: 김치찌개(이) → 진행 중(통계에서 뺀다)
 * 다른 조직의 투표는 섞이지 않는다.
 */
class MenuStatsIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	MenuService menuService;

	@Autowired
	VoteService voteService;

	User kim;

	User lee;

	User park;

	Long orgId;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
		inviteService.join(org.inviteToken(), park.getId());

		clock.set(2026, 9, 21, 11, 0);
		Long monday = createPoll(orgId, kim);
		Long kimchi = addOption(monday, kim, "김치찌개");
		vote(monday, kim, kimchi);
		vote(monday, lee, kimchi);
		vote(monday, park, addOption(monday, park, "돈까스"));
		addOption(monday, lee, "냉면");

		clock.set(2026, 9, 25, 11, 0);
		Long friday = createPoll(orgId, kim);
		vote(friday, kim, addOption(friday, kim, "김치 찌개"));
		addOption(friday, lee, "마라탕");

		clock.set(2026, 9, 30, 11, 0);
		Long today = createPoll(orgId, kim);
		vote(today, lee, addOption(today, lee, "김치찌개"));

		User other = userRepository.save(new User("sub-o", "o@example.com", "다른팀원", null));
		Long otherOrg = organizationService.create(other.getId(), "다른팀").id();
		clock.set(2026, 9, 29, 11, 0);
		Long otherPoll = createPoll(otherOrg, other);
		vote(otherPoll, other, addOption(otherPoll, other, "돈까스"));

		clock.set(2026, 9, 30, 11, 10);
	}

	@Test
	void 마감된_투표에서_참여자가_있던_메뉴를_띄어쓰기_무시하고_센다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-stats").with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.days").value(nullValue()))
			.andExpect(jsonPath("$.pollCount").value(2))
			.andExpect(jsonPath("$.menus", hasSize(2)))
			.andExpect(jsonPath("$.menus[0].name").value("김치 찌개"))
			.andExpect(jsonPath("$.menus[0].times").value(2))
			.andExpect(jsonPath("$.menus[0].people").value(3))
			.andExpect(jsonPath("$.menus[0].lastEatenOn").value("2026-09-25"))
			.andExpect(jsonPath("$.menus[1].name").value("돈까스"))
			.andExpect(jsonPath("$.menus[1].times").value(1))
			.andExpect(jsonPath("$.menus[1].lastEatenOn").value("2026-09-21"));
	}

	@Test
	void 최근_며칠만_볼_수_있다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-stats").param("days", "7").with(loginAs(lee)))
			.andExpect(jsonPath("$.from").value("2026-09-24"))
			.andExpect(jsonPath("$.pollCount").value(1))
			.andExpect(jsonPath("$.menus[*].name", contains("김치 찌개")))
			.andExpect(jsonPath("$.menus[0].times").value(1));
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-stats").param("days", "0").with(loginAs(lee)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 추천은_먹은_적이_있지만_최근_7일_안에는_먹지_않은_메뉴다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-recommendations").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("돈까스")))
			.andExpect(jsonPath("$[0].times").value(1));

		clock.set(2026, 10, 7, 11, 0); // 오늘(9/30) 투표도 마감됐고, 김치찌개를 먹은 날이 모두 7일 밖이다
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-recommendations").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("김치찌개", "돈까스")))
			.andExpect(jsonPath("$[0].times").value(3));
	}

	@Test
	void 자동완성은_마지막으로_먹은_날을_알려주고_최근에_먹은_메뉴를_뒤로_보낸다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-names").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("마라탕", "냉면", "돈까스", "김치찌개", "김치 찌개")))
			.andExpect(jsonPath("$[0].lastEatenOn").value(nullValue()))
			.andExpect(jsonPath("$[2].lastEatenOn").value("2026-09-21"))
			.andExpect(jsonPath("$[3].lastEatenOn").value("2026-09-25"));
	}

	@Test
	void 조직_멤버가_아니면_통계를_볼_수_없다() throws Exception {
		User stranger = userRepository.save(new User("sub-x", "x@example.com", "외부인", null));
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-stats").with(loginAs(stranger)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-recommendations").with(loginAs(stranger)))
			.andExpect(status().isNotFound());
	}

	private Long createPoll(Long org, User user) {
		return pollService.create(org, user.getId(), new PollRequest("점심", "11:50")).id();
	}

	private Long addOption(Long pollId, User user, String name) {
		return optionId(menuService.add(pollId, user.getId(), name, null).options(), name);
	}

	private static Long optionId(List<PollDetailResponse.Option> options, String name) {
		return options.stream().filter(o -> o.name().equals(name)).findFirst().orElseThrow().id();
	}

	private void vote(Long pollId, User user, Long optionId) {
		voteService.vote(pollId, user.getId(), optionId);
	}
}
