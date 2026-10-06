package com.ohjumwhat.admin;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.user.UserService;

/** 관리자 회원 프로필 수정(별명·소개·상세 프로필, 이슈 #93). 규칙과 문구는 마이페이지와 같다. */
class AdminProfileEditIntegrationTest extends IntegrationTest {

	static final String VALID_DETAILS = """
			{"mbti": "ENFP", "personalColor": "AUTUMN_WARM", "hobbies": ["등산", "독서"], "age": 32,
			 "jobTitle": "개발팀 매니저"}""";

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	User admin;

	User kim;

	User lee;

	/** 김철수·이영희가 함께 있는 조직 */
	Long devOrgId;

	@BeforeEach
	void setUp() {
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		OrganizationResponse dev = organizationService.create(kim.getId(), "개발팀");
		devOrgId = dev.id();
		inviteService.join(dev.inviteToken(), lee.getId());
	}

	@Test
	void 별명을_바꾸면_본인_정보와_멤버_목록에_보이고_비우면_구글_이름으로_돌아간다() throws Exception {
		edit(admin, "/nickname", lee, "{\"nickname\": \"  점심   요정 \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.id").value(lee.getId()))
			.andExpect(jsonPath("$.user.name").value("점심 요정"))
			.andExpect(jsonPath("$.user.googleName").value("이영희"))
			.andExpect(jsonPath("$.nickname").value("점심 요정"));

		mockMvc.perform(get("/api/me").with(loginAs(lee)))
			.andExpect(jsonPath("$.name").value("점심 요정"))
			.andExpect(jsonPath("$.nickname").value("점심 요정"));
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("김철수", "점심 요정")));

		edit(admin, "/nickname", lee, "{\"nickname\": \"  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.name").value("이영희"))
			.andExpect(jsonPath("$.nickname").value(nullValue()));
		mockMvc.perform(get("/api/me").with(loginAs(lee)))
			.andExpect(jsonPath("$.name").value("이영희"))
			.andExpect(jsonPath("$.nickname").value(nullValue()));
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("김철수", "이영희")));
	}

	@Test
	void 잘못된_별명은_마이페이지와_같은_문구로_거절하고_그대로_둔다() throws Exception {
		userService.changeNickname(lee.getId(), "점심 요정");

		edit(admin, "/nickname", lee, "{\"nickname\": \"" + "가".repeat(21) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이름은 20자 이하로 입력해 주세요."));
		edit(admin, "/nickname", lee, "{\"nickname\": \"" + "가".repeat(101) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이름은 20자 이하로 입력해 주세요."));
		edit(admin, "/nickname", lee, "{\"nickname\": \"점심\\u0007요정\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이름에 쓸 수 없는 문자가 있어요."));

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.name").value("점심 요정"))
			.andExpect(jsonPath("$.nickname").value("점심 요정"));
	}

	@Test
	void 관리자가_지운_별명은_옛_엔티티를_저장하거나_로그인해도_돌아오지_않는다() throws Exception {
		userService.changeNickname(lee.getId(), "나쁜 이름");
		// 관리자가 지우기 전에 읽어 둔 회원(옛 별명을 들고 있다)
		User stale = userRepository.findById(lee.getId()).orElseThrow();

		edit(admin, "/nickname", lee, "{\"nickname\": \"\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.name").value("이영희"))
			.andExpect(jsonPath("$.nickname").value(nullValue()));
		userRepository.save(stale);
		userService.login("sub-lee", "lee@example.com", true, "이영희(새 이름)", null);

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.name").value("이영희(새 이름)"))
			.andExpect(jsonPath("$.user.googleName").value("이영희(새 이름)"))
			.andExpect(jsonPath("$.nickname").value(nullValue()));
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("김철수", "이영희(새 이름)")));
	}

	@Test
	void 소개를_바꾸면_본인_정보와_멤버_목록에_보이고_비우면_지운다() throws Exception {
		edit(admin, "/profile", lee, """
				{"bio": "  국물   요리가 좋아요 ", "foodTags": ["#마라탕", " 김치 찌개 ", "김치찌개", "  "]}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value("국물 요리가 좋아요"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕", "김치 찌개")));

		mockMvc.perform(get("/api/me").with(loginAs(lee)))
			.andExpect(jsonPath("$.bio").value("국물 요리가 좋아요"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕", "김치 찌개")));
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[1].bio").value("국물 요리가 좋아요"))
			.andExpect(jsonPath("$[1].foodTags", contains("마라탕", "김치 찌개")));

		edit(admin, "/profile", lee, "{\"bio\": \"  \", \"foodTags\": []}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.foodTags", empty()));
		mockMvc.perform(get("/api/me").with(loginAs(lee)))
			.andExpect(jsonPath("$.bio").value(nullValue()))
			.andExpect(jsonPath("$.foodTags", empty()));
	}

	@Test
	void 잘못된_소개는_마이페이지와_같은_문구로_거절하고_그대로_둔다() throws Exception {
		userService.changeIntro(lee.getId(), "국물파", List.of("마라탕"));

		edit(admin, "/profile", lee, "{\"bio\": \"" + "가".repeat(51) + "\", \"foodTags\": []}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한줄 소개는 50자 이하로 입력해 주세요."));
		edit(admin, "/profile", lee, "{\"bio\": null, \"foodTags\": [\"1\", \"2\", \"3\", \"4\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("좋아하는 음식은 3개까지 적을 수 있어요."));
		edit(admin, "/profile", lee, "{\"bio\": null, \"foodTags\": [\"" + "가".repeat(11) + "\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("음식 이름은 10자 이하로 입력해 주세요."));

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.bio").value("국물파"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕")));
	}

	@Test
	void 상세_프로필을_바꾸면_정리해서_저장하고_본인_정보와_멤버_목록에_보인다() throws Exception {
		edit(admin, "/profile/details", lee, """
				{"mbti": " enfp ", "personalColor": "autumn_warm", "hobbies": ["#등산", " 독서 ", "독서"], "age": 32,
				 "jobTitle": "  개발팀   매니저 "}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.mbti").value("ENFP"))
			.andExpect(jsonPath("$.details.personalColor").value("AUTUMN_WARM"))
			.andExpect(jsonPath("$.details.hobbies", contains("등산", "독서")))
			.andExpect(jsonPath("$.details.age").value(32))
			.andExpect(jsonPath("$.details.jobTitle").value("개발팀 매니저"));

		mockMvc.perform(get("/api/me").with(loginAs(lee)))
			.andExpect(jsonPath("$.details.mbti").value("ENFP"))
			.andExpect(jsonPath("$.details.jobTitle").value("개발팀 매니저"));
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[1].details.mbti").value("ENFP"))
			.andExpect(jsonPath("$[1].details.hobbies", contains("등산", "독서")));
	}

	@Test
	void 상세_프로필은_다섯_항목이_모두_있어야_하고_마이페이지와_같은_문구로_거절한다() throws Exception {
		edit(admin, "/profile/details", lee, VALID_DETAILS.replace("ENFP", "ENFX"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("4가지 성향을 모두 선택해 주세요."));
		// 일부만 보내도 저장하지 않는다(DB CHECK: 모두 비었거나 모두 채워졌거나).
		edit(admin, "/profile/details", lee, VALID_DETAILS.replace("\"age\": 32", "\"age\": null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("올바른 나이를 입력해 주세요. (1~120세)"));
		edit(admin, "/profile/details", lee, "{\"mbti\": \"ENFP\", \"personalColor\": \"AUTUMN_WARM\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("최소 1개 이상의 취미를 등록해 주세요."));
		edit(admin, "/profile/details", lee, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("4가지 성향을 모두 선택해 주세요."));

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.details").value(nullValue()));
	}

	@Test
	void 상세_프로필을_지우면_다섯_항목이_함께_비고_본인이_다시_채울_수_있다() throws Exception {
		userService.changeDetails(lee.getId(), "ISTJ", "WINTER_COOL", List.of("독서"), 40, "팀장");
		userService.changeIntro(lee.getId(), "국물파", List.of("마라탕"));

		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/profile/details").with(loginAs(admin))
			.with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details").value(nullValue()))
			.andExpect(jsonPath("$.bio").value("국물파"));
		mockMvc.perform(get("/api/me").with(loginAs(lee)))
			.andExpect(jsonPath("$.details").value(nullValue()))
			.andExpect(jsonPath("$.foodTags", contains("마라탕")));
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[1].details").value(nullValue()));

		// 비어 있어도 그대로 성공한다.
		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/profile/details").with(loginAs(admin))
			.with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details").value(nullValue()));

		mockMvc.perform(put("/api/me/profile/details").with(loginAs(lee)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID_DETAILS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.mbti").value("ENFP"));
	}

	@Test
	void 다른_관리자와_자기_자신의_프로필도_고칠_수_있다() throws Exception {
		kim.promote();
		userRepository.save(kim);

		edit(admin, "/nickname", kim, "{\"nickname\": \"점심 대장\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.name").value("점심 대장"))
			.andExpect(jsonPath("$.user.role").value("ADMIN"));
		edit(admin, "/profile/details", admin, VALID_DETAILS)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.mbti").value("ENFP"));
		mockMvc.perform(get("/api/me").with(loginAs(admin))).andExpect(jsonPath("$.details.mbti").value("ENFP"));
	}

	@Test
	void 일반_회원은_403_로그인하지_않으면_401이다() throws Exception {
		edit(kim, "/nickname", lee, "{\"nickname\": \"점심 요정\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("권한이 없어요."));
		edit(kim, "/profile", lee, "{\"bio\": \"국물파\", \"foodTags\": []}").andExpect(status().isForbidden());
		edit(kim, "/profile/details", lee, VALID_DETAILS).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/profile/details").with(loginAs(kim))
			.with(xsrf()))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/users/" + lee.getId() + "/nickname").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"점심 요정\"}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/me").with(loginAs(lee))).andExpect(jsonPath("$.name").value("이영희"));
	}

	@Test
	void CSRF_토큰이_없으면_거절한다() throws Exception {
		mockMvc.perform(put("/api/admin/users/" + lee.getId() + "/nickname").with(loginAs(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"점심 요정\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/profile/details").with(loginAs(admin)))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/me").with(loginAs(lee))).andExpect(jsonPath("$.name").value("이영희"));
	}

	@Test
	void 없는_회원은_404다() throws Exception {
		mockMvc.perform(put("/api/admin/users/999999/nickname").with(loginAs(admin)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"점심 요정\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("회원을 찾을 수 없어요."));
		mockMvc.perform(put("/api/admin/users/999999/profile").with(loginAs(admin)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bio\": \"국물파\", \"foodTags\": []}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/admin/users/999999/profile/details").with(loginAs(admin)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID_DETAILS))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/admin/users/999999/profile/details").with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("회원을 찾을 수 없어요."));
		// 없는 회원이면 입력이 잘못됐어도 404다(회원부터 확인한다).
		mockMvc.perform(put("/api/admin/users/999999/nickname").with(loginAs(admin)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"" + "가".repeat(21) + "\"}"))
			.andExpect(status().isNotFound());
	}

	private ResultActions edit(User actor, String path, User target, String json) throws Exception {
		return mockMvc.perform(put("/api/admin/users/" + target.getId() + path).with(loginAs(actor)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}
}
