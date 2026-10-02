package com.ohjumwhat.place;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.FakeKakaoLocalConfiguration;
import com.ohjumwhat.FakeKakaoLocalConfiguration.FakeKakaoLocal;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

class PlaceIntegrationTest extends IntegrationTest {

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

	@Autowired
	FakeKakaoLocal fakeKakaoLocal;

	User kim;

	User outsider;

	Long orgId;

	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		outsider = userRepository.save(new User("sub-out", "out@example.com", "외부인", null));
		orgId = organizationService.create(kim.getId(), "개발팀").id();
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@Test
	void 지도_설정을_알려준다() throws Exception {
		mockMvc.perform(get("/api/config").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.naverMapKeyId").value("test-naver-key"))
			.andExpect(jsonPath("$.placeSearch").value(true));
	}

	@Test
	void 회사와_메뉴별_식당_위치를_주고_찾지_못한_메뉴는_뺀다() throws Exception {
		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		Long withPlace = addMenu("칼국수", FakeKakaoLocalConfiguration.PLACE_ADDRESS);
		Long unknown = addMenu("냉면", "찾을 수 없는 주소");
		Long noAddress = addMenu("돈까스", null);

		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", ids(withPlace, unknown, noAddress))
			.with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.center.lat").value(FakeKakaoLocalConfiguration.OFFICE.lat()))
			.andExpect(jsonPath("$.center.lng").value(FakeKakaoLocalConfiguration.OFFICE.lng()))
			.andExpect(jsonPath("$.center.name").value("오점왓빌딩"))
			.andExpect(jsonPath("$.places", hasSize(1)))
			.andExpect(jsonPath("$.places[0].optionId").value(withPlace))
			.andExpect(jsonPath("$.places[0].lat").value(FakeKakaoLocalConfiguration.PLACE.lat()));
	}

	@Test
	void 메뉴_없이_부르면_회사_위치만_주고_회사_주소가_없으면_null이다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/places").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.center").value(nullValue()))
			.andExpect(jsonPath("$.places", hasSize(0)));

		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		mockMvc.perform(get("/api/orgs/" + orgId + "/places").with(loginAs(kim)))
			.andExpect(jsonPath("$.center.lat").value(FakeKakaoLocalConfiguration.OFFICE.lat()));
	}

	@Test
	void 다른_조직의_메뉴는_보지_않는다() throws Exception {
		User lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		Long otherOrg = organizationService.create(lee.getId(), "디자인팀").id();
		Long otherPoll = pollService.create(otherOrg, lee.getId(), new PollRequest("점심", "11:50")).id();
		Long otherOption = menuService
			.add(otherPoll, lee.getId(), "칼국수", new PlaceLink("https://map.kakao.com/1", "가게",
					FakeKakaoLocalConfiguration.PLACE_ADDRESS))
			.options()
			.getFirst()
			.id();

		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", ids(otherOption)).with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.places", hasSize(0)));
	}

	@Test
	void 카카오가_실패한_주소는_빼고_나머지를_준다() throws Exception {
		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		Long failing = addMenu("냉면", FakeKakaoLocalConfiguration.FAILING_ADDRESS);
		Long ok = addMenu("칼국수", FakeKakaoLocalConfiguration.PLACE_ADDRESS);

		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", ids(failing, ok)).with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.center.lat").value(FakeKakaoLocalConfiguration.OFFICE.lat()))
			.andExpect(jsonPath("$.places", hasSize(1)))
			.andExpect(jsonPath("$.places[0].optionId").value(ok));
	}

	@Test
	void 멤버가_아니면_404이고_카카오를_부르지_않는다() throws Exception {
		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		fakeKakaoLocal.reset();
		mockMvc.perform(get("/api/orgs/" + orgId + "/places").with(loginAs(outsider)))
			.andExpect(status().isNotFound());
		assertThat(fakeKakaoLocal.calls()).isZero();
	}

	@Test
	void 메뉴는_한_번에_30개까지다() throws Exception {
		String tooMany = LongStream.rangeClosed(1, 31).mapToObj(Long::toString).collect(Collectors.joining(","));
		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", tooMany).with(loginAs(kim)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한 번에 메뉴 30개까지 볼 수 있어요."));
	}

	private void setOffice(String address) {
		organizationService.changeLocation(orgId, kim.getId(), null,
				new PlaceLink("https://map.naver.com/p/entry/place/1", "오점왓빌딩"), address, null);
	}

	private Long addMenu(String name, String address) {
		PollDetailResponse detail = menuService.add(pollId, kim.getId(), name,
				new PlaceLink("https://map.naver.com/p/entry/place/2", name + "집", address));
		return detail.options().getLast().id();
	}

	private static String ids(Long... ids) {
		return List.of(ids).stream().map(String::valueOf).collect(Collectors.joining(","));
	}
}
