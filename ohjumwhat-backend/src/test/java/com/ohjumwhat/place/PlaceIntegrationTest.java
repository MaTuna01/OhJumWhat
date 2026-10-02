package com.ohjumwhat.place;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
	void 조직_위치와_메뉴별_식당_위치를_주고_찾지_못한_메뉴는_뺀다() throws Exception {
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
	void 메뉴_없이_부르면_조직_위치만_주고_조직_주소가_없으면_null이다() throws Exception {
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

	@Test
	void 근처_식당을_찾으면_이름_분류가_맞는_곳을_앞에_가까운_순으로_준다() throws Exception {
		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);

		// 카카오 정확도순 3페이지를 한 번에 받아, 이름·분류에 검색어가 있는 곳을 앞에 두고 각각 가까운 순으로 둔다.
		mockMvc.perform(get("/api/orgs/" + orgId + "/places/search").param("q", " 김치찌개 ").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.center.lat").value(FakeKakaoLocalConfiguration.OFFICE.lat()))
			.andExpect(jsonPath("$.places[*].name", contains("할매집 김치찌개", "김치찌개랑 역삼점", "명동교자")))
			.andExpect(jsonPath("$.places[*].matched", contains(true, true, false)))
			.andExpect(jsonPath("$.places[0].kakaoPlaceId").value("1001"))
			.andExpect(jsonPath("$.places[0].category").value("찌개,전골"))
			.andExpect(jsonPath("$.places[1].category").value("찌개,전골"))
			.andExpect(jsonPath("$.places[0].roadAddress").value("서울 강남구 테헤란로 10"))
			.andExpect(jsonPath("$.places[0].distance").value(150));
		assertThat(fakeKakaoLocal.lastRadius()).isEqualTo(1000);
		assertThat(fakeKakaoLocal.lastSort()).isEqualTo(KakaoLocal.Sort.ACCURACY);

		// 검색어가 없으면 근처 음식점을 가까운 순으로 둘러본다.
		mockMvc.perform(get("/api/orgs/" + orgId + "/places/search").with(loginAs(kim)))
			.andExpect(jsonPath("$.places", hasSize(3)))
			.andExpect(jsonPath("$.places[*].matched", contains(true, true, true)));
		assertThat(fakeKakaoLocal.lastSort()).isEqualTo(KakaoLocal.Sort.DISTANCE);
	}

	@Test
	void 조직_주소가_없거나_값이_올바르지_않거나_카카오가_실패하면_알려준다() throws Exception {
		mockMvc.perform(get("/api/orgs/" + orgId + "/places/search").param("q", "김치찌개").with(loginAs(kim)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조직 설정에서 조직 주소를 정하면 근처 식당을 찾을 수 있어요."));

		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		mockMvc.perform(get("/api/orgs/" + orgId + "/places/search").param("q", "가".repeat(51)).with(loginAs(kim)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("검색어는 50자 이하로 입력해 주세요."));
		mockMvc.perform(get("/api/orgs/" + orgId + "/places/search").param("q", FakeKakaoLocalConfiguration.FAILING_QUERY)
			.with(loginAs(kim)))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.message").value("식당 검색이 잠시 안 돼요. 잠시 뒤에 다시 해 주세요."));

		fakeKakaoLocal.reset();
		mockMvc.perform(get("/api/orgs/" + orgId + "/places/search").param("q", "김치찌개").with(loginAs(outsider)))
			.andExpect(status().isNotFound());
		assertThat(fakeKakaoLocal.calls()).isZero();
	}

	@Test
	void 카카오_식당은_고를_때와_같은_방법으로_다시_찾아_이름과_위치를_준다() throws Exception {
		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		Long near = addKakaoMenu("김치찌개", "1001", "김치찌개");
		Long secondPage = addKakaoMenu("김치볶음밥", "1003", "김치찌개");
		Long browsed = addKakaoMenu("칼국수", "1002", null);

		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", ids(near, secondPage, browsed))
			.with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.places", hasSize(3)))
			.andExpect(jsonPath("$.places[0].optionId").value(near))
			.andExpect(jsonPath("$.places[0].name").value("할매집 김치찌개"))
			.andExpect(jsonPath("$.places[0].category").value("찌개,전골"))
			.andExpect(jsonPath("$.places[0].roadAddress").value("서울 강남구 테헤란로 10"))
			.andExpect(jsonPath("$.places[1].name").value("김치찌개랑 역삼점"))
			.andExpect(jsonPath("$.places[2].name").value("명동교자"));
		// 모두 찾았으니 넓은 반경으로 한 번 더 보지 않는다.
		assertThat(fakeKakaoLocal.lastRadius()).isEqualTo(1000);
	}

	@Test
	void 같은_방법으로_못_찾은_카카오_식당은_넓은_반경을_가까운_순으로_한_번_더_본다() throws Exception {
		setOffice(FakeKakaoLocalConfiguration.OFFICE_ADDRESS);
		Long wide = addKakaoMenu("김치찌개", "1004", "김치찌개");
		Long gone = addKakaoMenu("냉면", "9999", "김치찌개");

		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", ids(wide, gone)).with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.places", hasSize(1)))
			.andExpect(jsonPath("$.places[0].optionId").value(wide))
			.andExpect(jsonPath("$.places[0].name").value("멀리김치찌개"));
		assertThat(fakeKakaoLocal.lastRadius()).isEqualTo(20_000);
		assertThat(fakeKakaoLocal.lastSort()).isEqualTo(KakaoLocal.Sort.DISTANCE);
	}

	@Test
	void 조직_위치를_모르면_카카오_식당은_다시_찾지_못한다() throws Exception {
		Long near = addKakaoMenu("김치찌개", "1001", "김치찌개");

		mockMvc.perform(get("/api/orgs/" + orgId + "/places").param("optionIds", ids(near)).with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.center").value(nullValue()))
			.andExpect(jsonPath("$.places", hasSize(0)));
	}

	private Long addKakaoMenu(String name, String kakaoPlaceId, String query) {
		return menuService.add(pollId, kim.getId(), name, PlaceLink.kakao(kakaoPlaceId, query)).options().getLast().id();
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
