package com.ohjumwhat.auth;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.Membership;
import com.ohjumwhat.organization.MembershipRepository;
import com.ohjumwhat.organization.Organization;
import com.ohjumwhat.organization.OrganizationRepository;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class AuthIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationRepository organizationRepository;

	@Autowired
	MembershipRepository membershipRepository;

	@Test
	void 로그인하지_않으면_API는_리다이렉트_대신_401을_준다() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void 내_정보를_조회한다() throws Exception {
		User user = userRepository.save(new User("sub-1", "kim@example.com", "김철수", null));

		mockMvc.perform(get("/api/me").with(loginAs(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId()))
			.andExpect(jsonPath("$.name").value("김철수"))
			.andExpect(jsonPath("$.email").value("kim@example.com"))
			.andExpect(jsonPath("$.lastVisitedOrgId").value(nullValue()))
			.andExpect(jsonPath("$.admin").value(false));
	}

	@Test
	void 회원이_삭제됐으면_내_정보는_401이다() throws Exception {
		User user = userRepository.save(new User("sub-1", "kim@example.com", "김철수", null));
		userRepository.delete(user);

		mockMvc.perform(get("/api/me").with(loginAs(user)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("다시 로그인해 주세요."));
	}

	@Test
	void 차단된_계정의_로그인_실패는_차단_안내로_보낸다() throws Exception {
		MockHttpServletResponse blocked = new MockHttpServletResponse();
		SecurityConfig.loginFailure().onAuthenticationFailure(new MockHttpServletRequest(), blocked,
				new OAuth2AuthenticationException(new OAuth2Error(GoogleOidcUserService.ACCOUNT_BLOCKED)));
		MockHttpServletResponse other = new MockHttpServletResponse();
		SecurityConfig.loginFailure().onAuthenticationFailure(new MockHttpServletRequest(), other,
				new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));

		assertThat(blocked.getRedirectedUrl()).isEqualTo("/login?error=blocked");
		assertThat(other.getRedirectedUrl()).isEqualTo("/login?error");
	}

	@Test
	void 최근_들어간_조직을_알려준다() throws Exception {
		User user = userRepository.save(new User("sub-1", "kim@example.com", "김철수", null));
		Organization older = organizationRepository.save(new Organization("A팀", "token-a"));
		Organization recent = organizationRepository.save(new Organization("B팀", "token-b"));
		Instant now = Instant.now();
		membershipRepository.save(new Membership(older.getId(), user.getId(), now.minusSeconds(60)));
		membershipRepository.save(new Membership(recent.getId(), user.getId(), now));

		mockMvc.perform(get("/api/me").with(loginAs(user)))
			.andExpect(jsonPath("$.lastVisitedOrgId").value(recent.getId()));
	}

	@Test
	void 로그인하지_않은_요청은_세션을_만들지_않는다() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(cookie().doesNotExist("SESSION"));
	}

	@Test
	void 응답에_CSRF_토큰_쿠키를_내려준다() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(cookie().exists("XSRF-TOKEN"));
	}

	@Test
	void 로그아웃은_CSRF_토큰이_있어야_한다() throws Exception {
		User user = userRepository.save(new User("sub-1", "kim@example.com", "김철수", null));

		mockMvc.perform(post("/logout").with(loginAs(user))).andExpect(status().isForbidden());
		mockMvc.perform(post("/logout").with(loginAs(user)).with(xsrf())).andExpect(status().isNoContent());
	}
}
