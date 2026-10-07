package com.ohjumwhat.sanction;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 제재 테스트 공통: 관리자, 김철수(제재 받는 사람)·이영희가 함께 있는 조직, 관리자 API로 제재 걸기·해제 */
abstract class SanctionTestBase extends IntegrationTest {

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected OrganizationService organizationService;

	@Autowired
	protected InviteService inviteService;

	@Autowired
	protected UserSanctionRepository sanctionRepository;

	protected User admin;

	protected User kim;

	protected User lee;

	/** 이영희가 만들고 김철수가 들어온 조직 */
	protected Long orgId;

	@BeforeEach
	void setUpSanctionBase() {
		clock.set(2026, 10, 7, 11, 0); // 수요일 오전 11:00 (한국 시간)
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", "https://lh3.googleusercontent.com/kim"));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		OrganizationResponse dev = organizationService.create(lee.getId(), "개발팀");
		orgId = dev.id();
		inviteService.join(dev.inviteToken(), kim.getId());
	}

	/**
	 * 별명·올린 사진·한줄 소개를 채운다(사진은 키만, 파일은 없다). 비어 있는 항목은 초기화하지 않고 기록에서도 빼므로, 초기화가
	 * 기록되는 것을 볼 때 먼저 부른다.
	 */
	protected void fillProfile(User user) {
		jdbcTemplate.update("""
				update users set nickname = '별명', photo_key = '0123456789abcdef0123456789abcdef', bio = '안녕하세요',
					food_tags = '{라멘}' where id = ?""", user.getId());
	}

	/** 관리자 API로 제재를 건다(본문 JSON 그대로). */
	protected ResultActions apply(User by, User target, String json) throws Exception {
		return mockMvc.perform(post("/api/admin/users/{userId}/sanctions", target.getId()).with(loginAs(by))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** 그 기능들을 days일(null이면 해제할 때까지) 막고 제재 ID를 준다. */
	protected long restrict(User target, Integer days, Restriction... restrictions) throws Exception {
		String names = Arrays.stream(restrictions).map(r -> "\"" + r.name() + "\"").collect(Collectors.joining(","));
		return sanction(target, "{\"restrictions\": [" + names + "], \"days\": " + days + ", \"reason\": \"ABUSE\"}");
	}

	/** 제재를 걸고(200이어야 한다) 제재 ID를 준다. */
	protected long sanction(User target, String json) throws Exception {
		String body = apply(admin, target, json).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	protected ResultActions lift(long sanctionId) throws Exception {
		return mockMvc.perform(post("/api/admin/sanctions/{sanctionId}/lift", sanctionId).with(loginAs(admin))
			.with(xsrf()));
	}
}
