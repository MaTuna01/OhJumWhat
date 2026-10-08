package com.ohjumwhat.common;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 테스트용 static/index.html·landing.html(src/test/resources)로 첫 화면과 SPA 포워딩을 확인한다. */
class SpaFallbackTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Test
	void 첫_화면은_로그인하지_않았으면_소개_페이지_로그인했으면_앱이다() throws Exception {
		User user = userRepository.save(new User("sub-1", "kim@example.com", "김철수", null));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("spa-landing")))
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(header().string(HttpHeaders.VARY, containsString(HttpHeaders.COOKIE)));
		mockMvc.perform(get("/").with(loginAs(user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("spa-index")))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}

	@Test
	void 화면_경로는_index_html을_돌려준다() throws Exception {
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
