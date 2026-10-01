package com.ohjumwhat.organization;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
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

import com.ohjumwhat.FakeNaverShortLinksConfiguration;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class OrganizationLocationIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	User kim;

	User outsider;

	Long orgId;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		outsider = userRepository.save(new User("sub-out", "out@example.com", "외부인", null));
		orgId = organizationService.create(kim.getId(), "개발팀").id();
	}

	@Test
	void 조직_위치를_저장하면_조직_정보에_함께_나온다() throws Exception {
		changeLocation(kim, " 역삼동 ", "[네이버 지도]\n오점왓빌딩\nnaver.me/" + FakeNaverShortLinksConfiguration.PLACE_CODE,
				"  오점왓\n 빌딩 ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.area").value("역삼동"))
			.andExpect(jsonPath("$.officeName").value("오점왓 빌딩"))
			.andExpect(jsonPath("$.officeLink").value("https://map.naver.com/p/entry/place/1868364770"));

		mockMvc.perform(get("/api/orgs/" + orgId).with(loginAs(kim)))
			.andExpect(jsonPath("$.area").value("역삼동"))
			.andExpect(jsonPath("$.officeName").value("오점왓 빌딩"));
	}

	@Test
	void 빈_값은_지우고_링크_없는_회사_이름은_버린다() throws Exception {
		changeLocation(kim, "역삼동", "https://map.kakao.com/123", "회사").andExpect(status().isOk());

		changeLocation(kim, "  ", "", "회사")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.area").value(nullValue()))
			.andExpect(jsonPath("$.officeName").value(nullValue()))
			.andExpect(jsonPath("$.officeLink").value(nullValue()));
	}

	@Test
	void 멤버가_아니면_404이고_값을_검사한다() throws Exception {
		changeLocation(outsider, "역삼동", null, null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(MembershipService.ORGANIZATION_NOT_FOUND));
		changeLocation(kim, "가".repeat(21), null, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("검색 지역은 20자 이하로 입력해 주세요."));
		changeLocation(kim, "역삼동", "회사", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요."));
	}

	private ResultActions changeLocation(User user, String area, String officeLink, String officeName)
			throws Exception {
		return mockMvc.perform(put("/api/orgs/" + orgId + "/location").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"area\": " + json(area) + ", \"officeLink\": " + json(officeLink) + ", \"officeName\": "
					+ json(officeName) + "}"));
	}

	/** 테스트 값에는 따옴표가 없으므로 줄바꿈만 이스케이프한다. */
	private static String json(String value) {
		return value == null ? "null" : "\"" + value.replace("\n", "\\n") + "\"";
	}
}
