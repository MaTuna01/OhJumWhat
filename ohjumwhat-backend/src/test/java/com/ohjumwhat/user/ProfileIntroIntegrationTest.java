package com.ohjumwhat.user;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;

class ProfileIntroIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	TransactionTemplate transactionTemplate;

	User kim;

	User lee;

	Long orgId;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
	}

	@Test
	void 처음에는_소개가_비어_있다() throws Exception {
		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.foodTags", empty()));
		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].bio").value(nullValue()))
			.andExpect(jsonPath("$[0].foodTags", empty()));
	}

	@Test
	void 소개를_정하면_내_정보와_같은_조직_멤버_목록에_보인다() throws Exception {
		changeIntro(kim, """
				{"bio": "  점심은   국물 요리가 좋아요 ", "foodTags": ["#마라탕", " 쌀국수 ", "김치 찌개", "김치찌개"]}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value("점심은 국물 요리가 좋아요"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕", "쌀국수", "김치 찌개")));

		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].bio").value("점심은 국물 요리가 좋아요"))
			.andExpect(jsonPath("$[0].foodTags", contains("마라탕", "쌀국수", "김치 찌개")))
			.andExpect(jsonPath("$[1].bio").value(nullValue()))
			.andExpect(jsonPath("$[1].foodTags", empty()));
	}

	@Test
	void 비우면_소개와_음식을_지운다() throws Exception {
		changeIntro(kim, """
				{"bio": "국물파", "foodTags": ["마라탕"]}""");

		changeIntro(kim, """
				{"bio": "   ", "foodTags": ["  ", "#"]}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.foodTags", empty()));
		changeIntro(kim, """
				{"bio": "국물파", "foodTags": ["마라탕"]}""");
		changeIntro(kim, "{}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.foodTags", empty()));
	}

	@Test
	void 너무_길거나_많거나_제어_문자가_있으면_거절하고_그대로_둔다() throws Exception {
		changeIntro(kim, """
				{"bio": "국물파", "foodTags": ["마라탕"]}""");

		changeIntro(kim, "{\"bio\": \"" + "가".repeat(51) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한줄 소개는 50자 이하로 입력해 주세요."));
		changeIntro(kim, "{\"bio\": \"줄\\u0007바꿈\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한줄 소개에 쓸 수 없는 문자가 있어요."));
		changeIntro(kim, """
				{"foodTags": ["마라탕", "쌀국수", "떡볶이", "초밥"]}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("좋아하는 음식은 3개까지 적을 수 있어요."));
		changeIntro(kim, "{\"foodTags\": [\"" + "가".repeat(11) + "\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("음식 이름은 10자 이하로 입력해 주세요."));
		changeIntro(kim, "{\"foodTags\": [\"마라\\u0000탕\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("음식 이름에 쓸 수 없는 문자가 있어요."));
		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.bio").value("국물파"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕")));

		// 글자 수는 글자(코드 포인트)로 세고, 띄어쓰기·대소문자만 다른 음식은 하나로 쳐서 3개를 넘지 않는다.
		changeIntro(kim, "{\"bio\": \"" + "😀".repeat(50) + "\", \"foodTags\": [\"" + "🍜".repeat(10)
				+ "\", \"Pho\", \"pho\", \"떡 볶이\", \"떡볶이\"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.foodTags", contains("🍜".repeat(10), "Pho", "떡 볶이")));
	}

	@Test
	void 다시_로그인해도_소개는_그대로다() throws Exception {
		changeIntro(kim, """
				{"bio": "국물파", "foodTags": ["마라탕"]}""");

		userService.login("sub-kim", "kim@example.com", true, "김철수(새 이름)", null);

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.googleName").value("김철수(새 이름)"))
			.andExpect(jsonPath("$.bio").value("국물파"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕")));
	}

	@Test
	void 소개를_바꾸는_순간_로그인이_겹쳐도_로그인이_옛_소개로_되돌리지_않는다() {
		// 로그인이 회원을 읽은 뒤, 커밋하기 전에 소개가 바뀐 상황
		transactionTemplate.executeWithoutResult(status -> {
			User user = userRepository.findById(kim.getId()).orElseThrow();
			jdbcTemplate.update("update users set bio = '국물파', food_tags = '{마라탕}' where id = ?", kim.getId());
			user.updateProfile("kim@example.com", "김철수", null);
			user.recordLogin(clock.instant());
			userRepository.flush();
		});

		User saved = userRepository.findById(kim.getId()).orElseThrow();
		assertThat(saved.getBio()).isEqualTo("국물파");
		assertThat(saved.getFoodTags()).containsExactly("마라탕");
	}

	@Test
	void 관리자는_회원_상세에서_소개를_본다() throws Exception {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		admin = userRepository.save(admin);
		changeIntro(lee, """
				{"bio": "국물파", "foodTags": ["마라탕", "쌀국수"]}""");

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.bio").value("국물파"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕", "쌀국수")));
	}

	@Test
	void 로그인하지_않으면_소개를_바꿀_수_없다() throws Exception {
		mockMvc.perform(put("/api/me/profile").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bio\": \"국물파\"}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions changeIntro(User user, String json) throws Exception {
		return mockMvc.perform(put("/api/me/profile").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}
}
