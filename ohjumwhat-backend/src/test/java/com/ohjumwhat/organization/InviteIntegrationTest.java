package com.ohjumwhat.organization;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class InviteIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Test
	void 초대_링크로_조직_정보를_보고_참여한다() throws Exception {
		User kim = userRepository.save(new User("sub-kim", "kim@example.com", "kim", null));
		User lee = userRepository.save(new User("sub-lee", "lee@example.com", "lee", null));
		OrganizationResponse org = organizationService.create(kim.getId(), "개발팀");

		mockMvc.perform(get("/api/invites/" + org.inviteToken()).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organizationId").value(org.id()))
			.andExpect(jsonPath("$.name").value("개발팀"))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.alreadyMember").value(false));

		mockMvc.perform(post("/api/invites/" + org.inviteToken() + "/join").with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organizationId").value(org.id()));

		mockMvc.perform(get("/api/invites/" + org.inviteToken()).with(loginAs(lee)))
			.andExpect(jsonPath("$.memberCount").value(2))
			.andExpect(jsonPath("$.alreadyMember").value(true));
	}

	@Test
	void 이미_멤버가_다시_참여해도_중복되지_않는다() throws Exception {
		User kim = userRepository.save(new User("sub-kim", "kim@example.com", "kim", null));
		OrganizationResponse org = organizationService.create(kim.getId(), "개발팀");

		mockMvc.perform(post("/api/invites/" + org.inviteToken() + "/join").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/invites/" + org.inviteToken()).with(loginAs(kim)))
			.andExpect(jsonPath("$.memberCount").value(1));
	}

	@Test
	void 없는_초대_토큰은_404() throws Exception {
		User kim = userRepository.save(new User("sub-kim", "kim@example.com", "kim", null));

		mockMvc.perform(get("/api/invites/no-such-token").with(loginAs(kim)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("유효하지 않은 초대 링크예요."));
		mockMvc.perform(post("/api/invites/no-such-token/join").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNotFound());
	}
}
