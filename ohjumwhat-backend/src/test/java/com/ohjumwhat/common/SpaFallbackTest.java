package com.ohjumwhat.common;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 테스트용 static/index.html(src/test/resources)로 SPA 포워딩을 확인한다. */
class SpaFallbackTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Test
	void 화면_경로는_index_html을_돌려준다() throws Exception {
		// "/"는 Spring의 웰컴 페이지 처리로 index.html에 forward된다.
		mockMvc.perform(get("/")).andExpect(forwardedUrl("index.html"));
		for (String path : new String[] { "/login", "/me", "/orgs/1/polls/2", "/invite/abc" }) {
			mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("spa-index")));
		}
	}

	@Test
	void 없는_파일과_없는_API는_404다() throws Exception {
		User user = userRepository.save(new User("sub-1", "kim@example.com", "김철수", null));

		mockMvc.perform(get("/missing.js")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/does-not-exist").with(loginAs(user))).andExpect(status().isNotFound());
	}
}
