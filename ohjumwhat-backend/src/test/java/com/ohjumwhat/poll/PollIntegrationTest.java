package com.ohjumwhat.poll;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class PollIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	User kim;

	User lee;

	Long orgId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0); // 수요일 오전 11:00 (한국 시간)
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
	}

	@Test
	void 투표를_만들면_바로_열리고_오늘_목록에_보인다() throws Exception {
		createPoll(kim, "점심", "11:50")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.closesAt").value("2026-09-30T02:50:00Z"))
			.andExpect(jsonPath("$.scheduled").value(false))
			.andExpect(jsonPath("$.memberCount").value(2))
			.andExpect(jsonPath("$.nonRespondents", hasSize(2)));

		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/today").with(loginAs(lee)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].title").value("점심"))
			.andExpect(jsonPath("$[0].status").value("OPEN"))
			.andExpect(jsonPath("$[0].myResponse").value("NONE"));
	}

	@Test
	void 마감_시간이_지금보다_이르면_400() throws Exception {
		createPoll(kim, "점심", "10:59")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("마감 시간은 지금보다 뒤여야 해요."));
		createPoll(kim, "점심", "25:00").andExpect(status().isBadRequest());
	}

	@Test
	void 메뉴를_추가해도_자동으로_참여하지_않고_같은_이름은_409() throws Exception {
		Long pollId = pollId(createPoll(kim, "점심", "11:50"));

		addOption(kim, pollId, "  김치찌개 ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[0].name").value("김치찌개"))
			.andExpect(jsonPath("$.options[0].createdBy.name").value("김철수"))
			.andExpect(jsonPath("$.options[0].voters", empty()))
			.andExpect(jsonPath("$.options[0].mine").value(true))
			.andExpect(jsonPath("$.options[0].deletable").value(true))
			.andExpect(jsonPath("$.myResponse").value("NONE"));

		addOption(lee, pollId, "김치찌개")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 있는 메뉴예요."));
	}

	@Test
	void 한_메뉴에_참여하고_바꾸고_패스해도_응답은_한_사람당_하나다() throws Exception {
		Long pollId = pollId(createPoll(kim, "점심", "11:50"));
		Long kimchi = optionId(addOption(kim, pollId, "김치찌개"), "김치찌개");
		Long donkatsu = optionId(addOption(kim, pollId, "돈까스"), "돈까스");

		vote(lee, pollId, kimchi)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myResponse").value("OPTION"))
			.andExpect(jsonPath("$.myOptionId").value(kimchi))
			.andExpect(jsonPath("$.options[0].voters[0].name").value("이영희"))
			.andExpect(jsonPath("$.soloOptionIds", contains(kimchi.intValue())))
			.andExpect(jsonPath("$.nonRespondents[*].name", contains("김철수")));

		vote(lee, pollId, donkatsu)
			.andExpect(jsonPath("$.options[0].voters", empty()))
			.andExpect(jsonPath("$.options[1].voters[0].name").value("이영희"));

		vote(lee, pollId, null)
			.andExpect(jsonPath("$.myResponse").value("PASS"))
			.andExpect(jsonPath("$.myOptionId").doesNotExist())
			.andExpect(jsonPath("$.passed[*].name", contains("이영희")))
			.andExpect(jsonPath("$.soloOptionIds", empty()));

		assertThat(jdbcTemplate.queryForObject("select count(*) from votes", Long.class)).isEqualTo(1);
	}

	@Test
	void 다른_투표의_메뉴로는_참여할_수_없다() throws Exception {
		Long pollA = pollId(createPoll(kim, "점심", "11:50"));
		Long pollB = pollId(createPoll(kim, "저녁", "18:00"));
		Long optionOfB = optionId(addOption(kim, pollB, "국밥"), "국밥");

		vote(lee, pollA, optionOfB)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이 투표의 메뉴가 아니에요."));
	}

	@Test
	void 마감되면_참여_메뉴_추가_삭제를_막고_결과만_보여준다() throws Exception {
		Long pollId = pollId(createPoll(kim, "점심", "11:50"));
		Long kimchi = optionId(addOption(kim, pollId, "김치찌개"), "김치찌개");
		Long empty = optionId(addOption(kim, pollId, "쌀국수"), "쌀국수");
		vote(lee, pollId, kimchi);

		clock.set(2026, 9, 30, 11, 50);

		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + pollId).with(loginAs(kim)))
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.options[1].deletable").value(false));
		vote(kim, pollId, kimchi).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("마감된 투표예요."));
		addOption(kim, pollId, "국밥").andExpect(status().isConflict());
		mockMvc.perform(delete("/api/polls/" + pollId + "/options/" + empty).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isConflict());
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/today").with(loginAs(kim)))
			.andExpect(jsonPath("$[0].status").value("CLOSED"))
			.andExpect(jsonPath("$[0].teamCount").value(1));
	}

	@Test
	void 메뉴는_추가한_사람만_참여자가_없을_때_삭제할_수_있다() throws Exception {
		Long pollId = pollId(createPoll(kim, "점심", "11:50"));
		Long kimchi = optionId(addOption(kim, pollId, "김치찌개"), "김치찌개");

		mockMvc.perform(delete("/api/polls/" + pollId + "/options/" + kimchi).with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isForbidden());

		vote(lee, pollId, kimchi);
		mockMvc.perform(delete("/api/polls/" + pollId + "/options/" + kimchi).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("참여한 사람이 있는 메뉴는 삭제할 수 없어요."));

		vote(lee, pollId, null);
		mockMvc.perform(delete("/api/polls/" + pollId + "/options/" + kimchi).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options", empty()));
	}

	@Test
	void 조직_멤버가_아니면_투표를_볼_수도_참여할_수도_없다() throws Exception {
		Long pollId = pollId(createPoll(kim, "점심", "11:50"));
		Long kimchi = optionId(addOption(kim, pollId, "김치찌개"), "김치찌개");
		User stranger = userRepository.save(new User("sub-x", "x@example.com", "외부인", null));
		Long otherOrg = organizationService.create(stranger.getId(), "다른팀").id();

		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + pollId).with(loginAs(stranger)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orgs/" + otherOrg + "/polls/" + pollId).with(loginAs(stranger)))
			.andExpect(status().isNotFound());
		vote(stranger, pollId, kimchi).andExpect(status().isNotFound());
		addOption(stranger, pollId, "국밥").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/today").with(loginAs(stranger)))
			.andExpect(status().isNotFound());
	}

	@Test
	void 메뉴_자동완성은_같은_조직의_지난_메뉴를_중복_없이_보여준다() throws Exception {
		Long yesterday = pollId(createPoll(kim, "어제 점심", "11:50"));
		addOption(kim, yesterday, "김치찌개");
		addOption(kim, yesterday, "순대국");
		Long today = pollId(createPoll(kim, "점심", "11:50"));
		addOption(kim, today, "김치찌개");
		User other = userRepository.save(new User("sub-o", "o@example.com", "다른팀원", null));
		Long otherOrg = organizationService.create(other.getId(), "다른팀").id();
		Long otherPoll = pollId(createPoll(other, otherOrg, "점심", "11:50"));
		addOption(other, otherPoll, "순두부찌개");

		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-names").param("q", "순").with(loginAs(lee)))
			.andExpect(jsonPath("$", contains("순대국")));
		mockMvc.perform(get("/api/orgs/" + orgId + "/menu-names").with(loginAs(lee)))
			.andExpect(jsonPath("$", containsInAnyOrder("김치찌개", "순대국")));
	}

	private ResultActions createPoll(User user, String title, String closesAt) throws Exception {
		return createPoll(user, orgId, title, closesAt);
	}

	private ResultActions createPoll(User user, Long org, String title, String closesAt) throws Exception {
		return mockMvc.perform(post("/api/orgs/" + org + "/polls").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\": \"" + title + "\", \"closesAt\": \"" + closesAt + "\"}"));
	}

	private ResultActions addOption(User user, Long pollId, String name) throws Exception {
		return mockMvc.perform(post("/api/polls/" + pollId + "/options").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"));
	}

	private ResultActions vote(User user, Long pollId, Long optionId) throws Exception {
		return mockMvc.perform(put("/api/polls/" + pollId + "/vote").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"optionId\": " + optionId + "}"));
	}

	private static Long pollId(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static Long optionId(ResultActions result, String name) throws Exception {
		List<Number> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(),
				"$.options[?(@.name == '" + name + "')].id");
		return ids.get(0).longValue();
	}
}
