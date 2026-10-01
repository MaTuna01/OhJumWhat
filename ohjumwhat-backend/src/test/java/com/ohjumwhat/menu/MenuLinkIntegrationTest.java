package com.ohjumwhat.menu;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class MenuLinkIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	MenuService menuService;

	User kim;

	User lee;

	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		inviteService.join(org.inviteToken(), lee.getId());
		pollId = pollService.create(org.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@Test
	void 메뉴를_추가할_때_지도_공유_문구를_붙이면_주소만_저장한다() throws Exception {
		addOption(kim, "김밥", "[네이버 지도]\\n김밥천국 강남점\\nhttps://naver.me/5abcDEF")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[0].link").value("https://naver.me/5abcDEF"));
		addOption(kim, "돈까스", null)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[1].link").value(nullValue()));
		addOption(kim, "국밥", "국밥집")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요."));
	}

	@Test
	void 네이버_지도_공유_링크는_장소의_정식_링크로_저장하고_확인하지_못하면_단축_링크를_둔다() throws Exception {
		addOption(kim, "칼국수", "[네이버 지도]\\n할머니칼국수\\nnaver.me/" + FakeNaverShortLinksConfiguration.PLACE_CODE)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[0].link").value("https://map.naver.com/p/entry/place/1868364770"));
		addOption(kim, "냉면", "https://naver.me/" + FakeNaverShortLinksConfiguration.FAVORITE_CODE)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[1].link").value("https://naver.me/" + FakeNaverShortLinksConfiguration.FAVORITE_CODE));
		addOption(kim, "쌀국수", "https://map.naver.com/p/search/쌀국수/place/1868364770?c=15.00,0,0,0,dh")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[2].link").value("https://map.naver.com/p/entry/place/1868364770"));
	}

	@Test
	void 링크는_메뉴를_추가한_사람만_진행_중에_달고_고치고_지울_수_있다() throws Exception {
		Long optionId = menuService.add(pollId, kim.getId(), "김치찌개", null).options().getFirst().id();

		changeLink(lee, optionId, "https://naver.me/x")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("메뉴를 추가한 사람만 링크를 고칠 수 있어요."));
		changeLink(kim, optionId, "map.kakao.com/123")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[0].link").value("https://map.kakao.com/123"));
		changeLink(kim, optionId, "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[0].link").value(nullValue()));
		changeLink(kim, optionId, "javascript:alert(1)").andExpect(status().isBadRequest());

		clock.set(2026, 9, 30, 11, 50);
		changeLink(kim, optionId, "https://naver.me/x").andExpect(status().isConflict());
	}

	@Test
	void 식당_이름은_링크와_함께_저장하고_링크를_빼면_함께_지운다() throws Exception {
		addPlace(kim, "칼국수", "naver.me/" + FakeNaverShortLinksConfiguration.PLACE_CODE, "  할머니\n 칼국수 ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[0].placeName").value("할머니 칼국수"));
		addPlace(kim, "돈까스", null, "링크 없는 이름")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[1].link").value(nullValue()))
			.andExpect(jsonPath("$.options[1].placeName").value(nullValue()));
		addPlace(kim, "국밥", "https://map.kakao.com/123", "가".repeat(101))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("식당 이름은 100자 이하로 입력해 주세요."));

		Long optionId = menuService.add(pollId, kim.getId(), "김치찌개", null).options().getLast().id();
		changePlace(kim, optionId, "https://map.kakao.com/123", "김치찌개 명가")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[2].placeName").value("김치찌개 명가"));
		changePlace(kim, optionId, "", "김치찌개 명가")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[2].link").value(nullValue()))
			.andExpect(jsonPath("$.options[2].placeName").value(nullValue()));
	}

	private ResultActions addPlace(User user, String name, String link, String placeName) throws Exception {
		return mockMvc.perform(post("/api/polls/" + pollId + "/options").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"" + name + "\", \"link\": " + json(link) + ", \"placeName\": " + json(placeName) + "}"));
	}

	private ResultActions changePlace(User user, Long optionId, String link, String placeName) throws Exception {
		return mockMvc.perform(put("/api/polls/" + pollId + "/options/" + optionId + "/link").with(loginAs(user))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"link\": " + json(link) + ", \"placeName\": " + json(placeName) + "}"));
	}

	/** 테스트 값에는 따옴표가 없으므로 줄바꿈만 이스케이프한다. */
	private static String json(String value) {
		return value == null ? "null" : "\"" + value.replace("\n", "\\n") + "\"";
	}

	private ResultActions addOption(User user, String name, String link) throws Exception {
		String linkJson = link == null ? "null" : "\"" + link + "\"";
		return mockMvc.perform(post("/api/polls/" + pollId + "/options").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"" + name + "\", \"link\": " + linkJson + "}"));
	}

	private ResultActions changeLink(User user, Long optionId, String link) throws Exception {
		return mockMvc.perform(put("/api/polls/" + pollId + "/options/" + optionId + "/link").with(loginAs(user))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"link\": \"" + link + "\"}"));
	}
}
