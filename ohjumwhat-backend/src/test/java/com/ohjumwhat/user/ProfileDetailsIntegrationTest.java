package com.ohjumwhat.user;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;

class ProfileDetailsIntegrationTest extends IntegrationTest {

	static final String VALID = """
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
	void 처음에는_상세_프로필이_비어_있다() throws Exception {
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.details").value(nullValue()));
		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].details").value(nullValue()))
			.andExpect(jsonPath("$[1].details").value(nullValue()));
	}

	@Test
	void 상세_프로필을_정하면_내_정보와_같은_조직_멤버_목록에_보인다() throws Exception {
		changeDetails(kim, """
				{"mbti": " enfp ", "personalColor": "AUTUMN_WARM",
				 "hobbies": ["#등산", " 독서 ", "보드 게임", "보드게임", "  "], "age": 32, "jobTitle": "  개발팀   매니저 "}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.mbti").value("ENFP"))
			.andExpect(jsonPath("$.details.personalColor").value("AUTUMN_WARM"))
			.andExpect(jsonPath("$.details.hobbies", contains("등산", "독서", "보드 게임")))
			.andExpect(jsonPath("$.details.age").value(32))
			.andExpect(jsonPath("$.details.jobTitle").value("개발팀 매니저"));

		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].details.mbti").value("ENFP"))
			.andExpect(jsonPath("$[0].details.hobbies", contains("등산", "독서", "보드 게임")))
			.andExpect(jsonPath("$[0].details.age").value(32))
			.andExpect(jsonPath("$[1].details").value(nullValue()));
	}

	@Test
	void 관리자는_회원_상세에서_상세_프로필을_본다() throws Exception {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		admin = userRepository.save(admin);
		changeDetails(lee, VALID);

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.details.mbti").value("ENFP"))
			.andExpect(jsonPath("$.details.personalColor").value("AUTUMN_WARM"))
			.andExpect(jsonPath("$.details.jobTitle").value("개발팀 매니저"));
		mockMvc.perform(get("/api/admin/users/" + kim.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.details").value(nullValue()));
	}

	@Test
	void 항목이_빠지거나_잘못되면_기획서_문구로_거절하고_그대로_둔다() throws Exception {
		changeDetails(kim, VALID);

		expectRejected("\"mbti\": null", "4가지 성향을 모두 선택해 주세요.");
		expectRejected("\"mbti\": \"ENF\"", "4가지 성향을 모두 선택해 주세요.");
		expectRejected("\"mbti\": \"ENFX\"", "4가지 성향을 모두 선택해 주세요.");
		expectRejected("\"personalColor\": null", "퍼스널컬러를 선택해 주세요.");
		expectRejected("\"personalColor\": \"SPRING_WATER\"", "퍼스널컬러를 선택해 주세요.");
		expectRejected("\"hobbies\": null", "최소 1개 이상의 취미를 등록해 주세요.");
		expectRejected("\"hobbies\": []", "최소 1개 이상의 취미를 등록해 주세요.");
		expectRejected("\"hobbies\": [\" \", \"#\"]", "최소 1개 이상의 취미를 등록해 주세요.");
		expectRejected("\"hobbies\": [\"1\", \"2\", \"3\", \"4\", \"5\", \"6\"]", "취미는 5개까지 적을 수 있어요.");
		expectRejected("\"hobbies\": [\"" + "가".repeat(11) + "\"]", "취미는 10자 이하로 입력해 주세요.");
		expectRejected("\"hobbies\": [\"등\\u0007산\"]", "취미에 쓸 수 없는 문자가 있어요.");
		expectRejected("\"age\": null", "올바른 나이를 입력해 주세요. (1~120세)");
		expectRejected("\"age\": 0", "올바른 나이를 입력해 주세요. (1~120세)");
		expectRejected("\"age\": 121", "올바른 나이를 입력해 주세요. (1~120세)");
		expectRejected("\"jobTitle\": null", "직급을 입력해 주세요. (최대 15자)");
		expectRejected("\"jobTitle\": \"   \"", "직급을 입력해 주세요. (최대 15자)");
		expectRejected("\"jobTitle\": \"" + "가".repeat(16) + "\"", "직급을 입력해 주세요. (최대 15자)");
		expectRejected("\"jobTitle\": \"팀\\u0000장\"", "직급에 쓸 수 없는 문자가 있어요.");
		// 큰 요청을 막는 방어선(@Size)에 걸려도 기획서 문구로 답한다.
		expectRejected("\"jobTitle\": \"" + "가".repeat(101) + "\"", "직급을 입력해 주세요. (최대 15자)");

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.details.mbti").value("ENFP"))
			.andExpect(jsonPath("$.details.hobbies", contains("등산", "독서")))
			.andExpect(jsonPath("$.details.age").value(32))
			.andExpect(jsonPath("$.details.jobTitle").value("개발팀 매니저"));
	}

	@Test
	void 대소문자와_유니코드_공백은_정리해서_받는다() throws Exception {
		changeDetails(kim, """
				{"mbti": "infj", "personalColor": "summer_cool", "hobbies": ["보드\\u3000\\u3000게임"], "age": 29,
				 "jobTitle": "팀\\u00a0\\u00a0리드"}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.mbti").value("INFJ"))
			.andExpect(jsonPath("$.details.personalColor").value("SUMMER_COOL"))
			.andExpect(jsonPath("$.details.hobbies", contains("보드 게임")))
			.andExpect(jsonPath("$.details.jobTitle").value("팀 리드"));
	}

	@Test
	void 나이가_숫자가_아니면_거절한다() throws Exception {
		changeDetails(kim, VALID.replace("\"age\": 32", "\"age\": \"서른\""))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.details").value(nullValue()));
	}

	@Test
	void 모두_비었으면_첫_항목의_문구로_답한다() throws Exception {
		changeDetails(kim, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("4가지 성향을 모두 선택해 주세요."));
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.details").value(nullValue()));
	}

	@Test
	void 글자_수는_글자로_센다() throws Exception {
		changeDetails(kim, """
				{"mbti": "ISTJ", "personalColor": "WINTER_COOL", "hobbies": ["%s", "Golf", "golf", "보드 게임", "보드게임"],
				 "age": 120, "jobTitle": "%s"}""".formatted("🎮".repeat(10), "😀".repeat(15)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.hobbies", contains("🎮".repeat(10), "Golf", "보드 게임")))
			.andExpect(jsonPath("$.details.age").value(120))
			.andExpect(jsonPath("$.details.jobTitle").value("😀".repeat(15)));
	}

	@Test
	void 소개를_바꿔도_상세_프로필은_그대로고_그_반대도_같다() throws Exception {
		changeDetails(kim, VALID);
		mockMvc.perform(put("/api/me/profile").with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bio\": \"국물파\", \"foodTags\": [\"마라탕\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.details.mbti").value("ENFP"));

		changeDetails(kim, VALID.replace("ENFP", "INTJ"))
			.andExpect(jsonPath("$.bio").value("국물파"))
			.andExpect(jsonPath("$.foodTags", contains("마라탕")))
			.andExpect(jsonPath("$.details.mbti").value("INTJ"));
	}

	@Test
	void 다시_로그인해도_상세_프로필은_그대로다() throws Exception {
		changeDetails(kim, VALID);

		userService.login("sub-kim", "kim@example.com", true, "김철수(새 이름)", null);

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.googleName").value("김철수(새 이름)"))
			.andExpect(jsonPath("$.details.mbti").value("ENFP"))
			.andExpect(jsonPath("$.details.jobTitle").value("개발팀 매니저"));
	}

	@Test
	void 상세_프로필을_바꾸는_순간_로그인이_겹쳐도_로그인이_옛_값으로_되돌리지_않는다() {
		// 로그인이 회원을 읽은 뒤, 커밋하기 전에 상세 프로필이 바뀐 상황
		transactionTemplate.executeWithoutResult(status -> {
			User user = userRepository.findById(kim.getId()).orElseThrow();
			jdbcTemplate.update("""
					update users set mbti = 'ENFP', personal_color = 'AUTUMN_WARM', hobbies = '{등산}', age = 32,
					    job_title = '매니저' where id = ?""", kim.getId());
			user.updateProfile("kim@example.com", "김철수", null);
			user.recordLogin(clock.instant());
			userRepository.flush();
		});

		ProfileDetailsResponse details = userRepository.findById(kim.getId()).orElseThrow().getDetails();
		assertThat(details).isNotNull();
		assertThat(details.mbti()).isEqualTo("ENFP");
		assertThat(details.hobbies()).containsExactly("등산");
		assertThat(details.age()).isEqualTo(32);
	}

	@Test
	void DB는_다섯_항목이_함께_비었거나_함께_채워진_상태만_받는다() {
		assertThatThrownBy(() -> jdbcTemplate.update("update users set mbti = 'ENFP' where id = ?", kim.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 로그인하지_않으면_상세_프로필을_바꿀_수_없다() throws Exception {
		mockMvc.perform(put("/api/me/profile/details").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID))
			.andExpect(status().isUnauthorized());
	}

	/** VALID에서 한 항목만 바꿔 보내면 그 항목의 문구로 400이다. */
	private void expectRejected(String field, String message) throws Exception {
		String name = field.substring(0, field.indexOf(':'));
		String json = VALID.replaceFirst(Pattern.quote(name) + ":\\s*(\"[^\"]*\"|\\[[^\\]]*\\]|\\d+)",
				Matcher.quoteReplacement(field));
		changeDetails(kim, json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(message));
	}

	private ResultActions changeDetails(User user, String json) throws Exception {
		return mockMvc.perform(put("/api/me/profile/details").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}
}
