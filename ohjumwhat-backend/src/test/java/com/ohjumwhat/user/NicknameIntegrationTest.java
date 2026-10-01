package com.ohjumwhat.user;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.vote.VoteService;

class NicknameIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserService userService;

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

	Long orgId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
	}

	@Test
	void 별명을_정하면_내_정보와_멤버_목록과_투표_명단에_별명이_보인다() throws Exception {
		changeNickname(kim, "  점심   요정 ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("점심 요정"))
			.andExpect(jsonPath("$.nickname").value("점심 요정"))
			.andExpect(jsonPath("$.googleName").value("김철수"));

		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(lee)))
			.andExpect(jsonPath("$[*].name", contains("점심 요정", "이영희")));

		Long pollId = pollService.create(orgId, lee.getId(), new PollRequest("점심", "11:50")).id();
		Long optionId = menuService.add(pollId, kim.getId(), "김치찌개", null).options().getFirst().id();
		voteService.vote(pollId, kim.getId(), optionId);
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + pollId).with(loginAs(lee)))
			.andExpect(jsonPath("$.options[0].createdBy.name").value("점심 요정"))
			.andExpect(jsonPath("$.options[0].voters[0].name").value("점심 요정"))
			.andExpect(jsonPath("$.nonRespondents[0].name").value("이영희"));
	}

	@Test
	void 다시_로그인해도_별명은_그대로이고_구글_이름만_갱신된다() throws Exception {
		changeNickname(kim, "점심요정");

		userService.login("sub-kim", "kim@example.com", true, "김철수(새 이름)", null);

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.name").value("점심요정"))
			.andExpect(jsonPath("$.googleName").value("김철수(새 이름)"));
	}

	@Test
	void 비우면_구글_이름으로_돌아가고_너무_길거나_제어_문자는_거절한다() throws Exception {
		changeNickname(kim, "점심요정");

		changeNickname(kim, "   ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("김철수"))
			.andExpect(jsonPath("$.nickname").value(nullValue()));
		changeNickname(kim, "가".repeat(21))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이름은 20자 이하로 입력해 주세요."));
		changeNickname(kim, "😀".repeat(20)).andExpect(status().isOk());
		changeNickname(kim, "줄\\u0007바꿈")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이름에 쓸 수 없는 문자가 있어요."));
		assertThat(userRepository.findById(kim.getId()).orElseThrow().getNickname()).isEqualTo("😀".repeat(20));
	}

	@Test
	void 로그인하지_않으면_별명을_바꿀_수_없다() throws Exception {
		mockMvc.perform(put("/api/me/nickname").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"점심요정\"}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions changeNickname(User user, String nickname) throws Exception {
		return mockMvc.perform(put("/api/me/nickname").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"" + nickname + "\"}"));
	}
}
