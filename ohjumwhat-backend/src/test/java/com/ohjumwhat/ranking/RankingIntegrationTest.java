package com.ohjumwhat.ranking;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.admin.AdminService;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteService;

/**
 * 조직 「개발팀」은 9/1에 만들었고 김·이·박·최 4명이다(채택에 필요한 인원은 30%를 올린 2명).
 * 오늘은 2026-10-07(수) 낮 12시다. 이번 주는 10/5(월)~10/11(일), 이번 달은 10/1~10/31이다.
 */
class RankingIntegrationTest extends IntegrationTest {

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
	VoteService voteService;

	@Autowired
	AdminService adminService;

	User kim;

	User lee;

	User park;

	User choi;

	Long orgId;

	String inviteToken;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		choi = userRepository.save(new User("sub-choi", "choi@example.com", "최지우", null));
		clock.set(2026, 9, 1, 9, 0);
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteToken = org.inviteToken();
		inviteService.join(inviteToken, lee.getId());
		inviteService.join(inviteToken, park.getId());
		inviteService.join(inviteToken, choi.getId());
	}

	@Test
	void 채택된_메뉴를_추가한_사람별로_세고_같은_횟수는_같은_순위다() throws Exception {
		// 10/5(월) 점심: 김치찌개(김 추가) 3명, 돈까스(이 추가) 1명 → 김
		Poll monday = poll(2026, 10, 5, 11, "11:50");
		Long kimchi = monday.add(kim, "김치찌개");
		Long donkatsu = monday.add(lee, "돈까스");
		monday.vote(kim, kimchi).vote(lee, kimchi).vote(park, kimchi).vote(choi, donkatsu);
		// 10/5(월) 저녁: 순대국(박 추가) 2명 → 박
		Poll mondayDinner = poll(2026, 10, 5, 17, "18:00");
		Long sundae = mondayDinner.add(park, "순대국");
		mondayDinner.vote(park, sundae).vote(choi, sundae);
		// 10/6(화) 점심: 마라탕(이 추가) 4명 → 이
		Poll tuesday = poll(2026, 10, 6, 11, "11:50");
		Long mala = tuesday.add(lee, "마라탕");
		tuesday.vote(kim, mala).vote(lee, mala).vote(park, mala).vote(choi, mala);
		// 10/6(화) 저녁: "김치 찌개"(김 먼저 추가) 1명, 냉면(박) 1명, 나머지 패스 → 같으면 먼저 추가한 메뉴라 김
		Poll tuesdayDinner = poll(2026, 10, 6, 17, "18:00");
		Long kimchi2 = tuesdayDinner.add(kim, "김치 찌개");
		Long naengmyeon = tuesdayDinner.add(park, "냉면");
		tuesdayDinner.vote(lee, kimchi2).vote(park, naengmyeon).vote(kim, null).vote(choi, null);
		// 10/7(수) 점심: 진행 중이라 세지 않는다
		Poll today = poll(2026, 10, 7, 11, "12:30");
		Long udon = today.add(choi, "우동");
		today.vote(kim, udon).vote(lee, udon).vote(park, udon).vote(choi, udon);
		clock.set(2026, 10, 7, 12, 0);

		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period").value("WEEK"))
			.andExpect(jsonPath("$.from").value("2026-10-05"))
			.andExpect(jsonPath("$.to").value("2026-10-11"))
			.andExpect(jsonPath("$.hasPrevious").value(true))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.closedPollCount").value(4))
			.andExpect(jsonPath("$.adoptedPollCount").value(4))
			// 박(월 저녁)이 이(화 점심)보다 먼저 1회에 도달했다
			.andExpect(jsonPath("$.entries[*].user.name", contains("김철수", "박민수", "이영희")))
			.andExpect(jsonPath("$.entries[*].rank", contains(1, 2, 2)))
			.andExpect(jsonPath("$.entries[*].count", contains(2, 1, 1)))
			// 띄어쓰기만 다른 이름은 묶고, 가장 최근 이름을 보여준다
			.andExpect(jsonPath("$.entries[0].topMenu").value("김치 찌개"))
			.andExpect(jsonPath("$.entries[0].menus", hasSize(1)))
			.andExpect(jsonPath("$.entries[0].menus[0].count").value(2))
			.andExpect(jsonPath("$.entries[0].menus[0].lastAdoptedOn").value("2026-10-06"))
			.andExpect(jsonPath("$.entries[1].topMenu").value("순대국"))
			.andExpect(jsonPath("$.me.rank").value(2))
			.andExpect(jsonPath("$.me.count").value(1));

		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(choi)))
			.andExpect(jsonPath("$.me.rank").value(nullValue()))
			.andExpect(jsonPath("$.me.count").value(0));
	}

	@Test
	void 공동_순위_다음은_그_인원만큼_건너뛰고_같은_때_도달했으면_이름_순이다() throws Exception {
		// 10/5·10/6 점심은 투표가 두 개씩 같은 시각에 마감된다.
		Poll a = poll(2026, 10, 5, 11, "11:50");
		Long aOption = a.add(lee, "마라탕");
		a.vote(kim, aOption).vote(lee, aOption);
		Poll b = poll(2026, 10, 5, 11, "11:50");
		Long bOption = b.add(kim, "돈까스");
		b.vote(park, bOption).vote(choi, bOption);
		Poll c = poll(2026, 10, 6, 11, "11:50");
		Long cOption = c.add(kim, "돈까스");
		c.vote(kim, cOption).vote(lee, cOption);
		Poll d = poll(2026, 10, 6, 11, "11:50");
		Long dOption = d.add(lee, "마라탕");
		d.vote(park, dOption).vote(choi, dOption);
		adopt(2026, 10, 7, park, "냉면");
		clock.set(2026, 10, 7, 12, 0);

		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(park)))
			.andExpect(jsonPath("$.entries[*].user.name", contains("김철수", "이영희", "박민수")))
			.andExpect(jsonPath("$.entries[*].rank", contains(1, 1, 3)))
			.andExpect(jsonPath("$.me.rank").value(3));
	}

	@Test
	void 대표_메뉴는_가장_많이_채택된_메뉴이고_같으면_최근_메뉴다() throws Exception {
		adopt(2026, 10, 1, lee, "쌀국수");
		adopt(2026, 10, 2, lee, "마라탕");
		adopt(2026, 10, 5, lee, "쌀 국수");
		adopt(2026, 10, 6, lee, "돈까스");
		clock.set(2026, 10, 7, 12, 0);

		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").param("period", "month").with(loginAs(kim)))
			.andExpect(jsonPath("$.from").value("2026-10-01"))
			.andExpect(jsonPath("$.to").value("2026-10-31"))
			.andExpect(jsonPath("$.entries[0].count").value(4))
			.andExpect(jsonPath("$.entries[0].topMenu").value("쌀 국수"))
			.andExpect(jsonPath("$.entries[0].menus[*].name", contains("쌀 국수", "돈까스", "마라탕")))
			.andExpect(jsonPath("$.entries[0].menus[*].count", contains(2, 1, 1)))
			.andExpect(jsonPath("$.entries[0].menus[0].lastAdoptedOn").value("2026-10-05"));
	}

	@Test
	void 메뉴를_고른_사람이_마감_당시_인원의_30퍼센트보다_적으면_채택하지_않고_결과에_알려준다() throws Exception {
		Poll monday = poll(2026, 10, 5, 11, "11:50");
		Long kimchi = monday.add(kim, "김치찌개");
		monday.vote(kim, kimchi).vote(park, null);
		// 마감 뒤에 들어온 사람은 마감 당시 인원에 넣지 않고, 응답하고 떠난 사람은 넣는다.
		clock.set(2026, 10, 6, 9, 0);
		User jung = userRepository.save(new User("sub-jung", "jung@example.com", "정하늘", null));
		inviteService.join(inviteToken, jung.getId());
		organizationService.leave(orgId, park.getId());
		clock.set(2026, 10, 7, 12, 0);

		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + monday.id).with(loginAs(kim)))
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.adoption.optionId").value(nullValue()))
			.andExpect(jsonPath("$.adoption.participants").value(1))
			.andExpect(jsonPath("$.adoption.headcount").value(4))
			.andExpect(jsonPath("$.adoption.required").value(2));
		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(kim)))
			.andExpect(jsonPath("$.closedPollCount").value(1))
			.andExpect(jsonPath("$.adoptedPollCount").value(0))
			.andExpect(jsonPath("$.entries", hasSize(0)));
	}

	@Test
	void 마감된_투표_상세에_채택_메뉴를_알려주고_진행_중이면_비운다() throws Exception {
		Poll poll = poll(2026, 10, 7, 11, "11:50");
		Long kimchi = poll.add(kim, "김치찌개");
		Long donkatsu = poll.add(lee, "돈까스");
		poll.vote(kim, donkatsu).vote(lee, kimchi);

		clock.set(2026, 10, 7, 11, 30);
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + poll.id).with(loginAs(kim)))
			.andExpect(jsonPath("$.adoption").value(nullValue()));

		clock.set(2026, 10, 7, 12, 0);
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + poll.id).with(loginAs(kim)))
			.andExpect(jsonPath("$.adoption.optionId").value(kimchi))
			.andExpect(jsonPath("$.adoption.participants").value(2))
			.andExpect(jsonPath("$.adoption.headcount").value(4))
			.andExpect(jsonPath("$.adoption.required").value(2));
	}

	@Test
	void 조직을_떠난_사람은_랭킹에서_빠지고_다시_들어오면_돌아온다() throws Exception {
		adopt(2026, 10, 5, lee, "마라탕");
		adopt(2026, 10, 6, kim, "김치찌개");
		clock.set(2026, 10, 7, 9, 0);
		organizationService.leave(orgId, lee.getId());
		clock.set(2026, 10, 7, 12, 0);

		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(kim)))
			.andExpect(jsonPath("$.adoptedPollCount").value(2))
			.andExpect(jsonPath("$.entries[*].user.name", contains("김철수")))
			.andExpect(jsonPath("$.entries[0].rank").value(1));

		inviteService.join(inviteToken, lee.getId());
		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(kim)))
			.andExpect(jsonPath("$.entries[*].user.name", contains("이영희", "김철수")))
			.andExpect(jsonPath("$.entries[*].rank", contains(1, 1)));
	}

	@Test
	void 채택_메뉴를_추가한_사람이_강제_탈퇴하면_아무도_받지_않는다() throws Exception {
		User admin = userRepository.save(new User("sub-admin", "admin@example.com", "관리자", null));
		Poll poll = poll(2026, 10, 5, 11, "11:50");
		Long naengmyeon = poll.add(park, "냉면");
		Long udon = poll.add(kim, "우동");
		poll.vote(kim, naengmyeon).vote(lee, naengmyeon).vote(choi, udon).vote(park, udon);
		clock.set(2026, 10, 7, 12, 0);
		adminService.withdraw(admin.getId(), park.getId());

		// 박의 응답이 지워져도 냉면(2명)이 채택이고, 2위인 우동(김)에게 넘기지 않는다.
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + poll.id).with(loginAs(kim)))
			.andExpect(jsonPath("$.adoption.optionId").value(naengmyeon));
		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(kim)))
			.andExpect(jsonPath("$.adoptedPollCount").value(1))
			.andExpect(jsonPath("$.entries", hasSize(0)));
	}

	@Test
	void 기간은_투표_날짜로_나눈다() throws Exception {
		adopt(2026, 9, 30, lee, "마라탕");
		adopt(2026, 10, 4, kim, "김치찌개");
		adopt(2026, 10, 5, park, "냉면");
		clock.set(2026, 10, 7, 12, 0);

		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").param("period", "week").param("date", "2026-10-04")
			.with(loginAs(kim)))
			.andExpect(jsonPath("$.from").value("2026-09-28"))
			.andExpect(jsonPath("$.to").value("2026-10-04"))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.entries[*].user.name", contains("이영희", "김철수")));
		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").param("period", "MONTH").param("date", "2026-09-15")
			.with(loginAs(kim)))
			.andExpect(jsonPath("$.from").value("2026-09-01"))
			.andExpect(jsonPath("$.to").value("2026-09-30"))
			.andExpect(jsonPath("$.entries[*].user.name", contains("이영희")));
		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").param("period", "month").with(loginAs(kim)))
			.andExpect(jsonPath("$.entries[*].user.name", contains("김철수", "박민수")));
	}

	@Test
	void 지난_기록은_12개월_전까지_보고_앞으로의_기간은_볼_수_없다() throws Exception {
		clock.set(2026, 10, 7, 12, 0);
		String url = "/api/orgs/" + orgId + "/ranking";

		mockMvc.perform(get(url).param("period", "month").param("date", "2025-10-15").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2025-10-01"))
			.andExpect(jsonPath("$.hasPrevious").value(false))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get(url).param("period", "month").param("date", "2025-11-01").with(loginAs(kim)))
			.andExpect(jsonPath("$.hasPrevious").value(true));
		// 12개월 전 1일이 들어 있는 주까지 본다
		mockMvc.perform(get(url).param("period", "week").param("date", "2025-09-29").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2025-09-29"))
			.andExpect(jsonPath("$.hasPrevious").value(false));
		mockMvc.perform(get(url).param("period", "month").param("date", "2025-09-30").with(loginAs(kim)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("랭킹은 12개월 전까지 볼 수 있어요."));
		mockMvc.perform(get(url).param("period", "week").param("date", "2025-09-28").with(loginAs(kim)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(url).param("date", "2026-10-08").with(loginAs(kim)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(url).param("period", "year").with(loginAs(kim)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(url).param("date", "10/07").with(loginAs(kim)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("날짜 형식이 올바르지 않아요."));
	}

	@Test
	void 조직_멤버가_아니면_랭킹을_볼_수_없다() throws Exception {
		User stranger = userRepository.save(new User("sub-x", "x@example.com", "외부인", null));
		mockMvc.perform(get("/api/orgs/" + orgId + "/ranking").with(loginAs(stranger)))
			.andExpect(status().isNotFound());
	}

	/** 그날 점심(11:00에 만들어 11:50 마감)에 user가 추가한 메뉴를 네 명 모두 골라 채택한다. */
	private void adopt(int year, int month, int day, User user, String menu) {
		Poll poll = poll(year, month, day, 11, "11:50");
		Long option = poll.add(user, menu);
		poll.vote(kim, option).vote(lee, option).vote(park, option).vote(choi, option);
	}

	/** 그날 hour시에 kim이 만든 투표(closesAt에 마감) */
	private Poll poll(int year, int month, int day, int hour, String closesAt) {
		clock.set(year, month, day, hour, 0);
		return new Poll(pollService.create(orgId, kim.getId(), new PollRequest("점심", closesAt)).id());
	}

	private class Poll {

		final Long id;

		Poll(Long id) {
			this.id = id;
		}

		Long add(User user, String name) {
			List<PollDetailResponse.Option> options = menuService.add(id, user.getId(), name, null).options();
			return options.stream().filter(o -> o.name().equals(name)).findFirst().orElseThrow().id();
		}

		Poll vote(User user, Long optionId) {
			voteService.vote(id, user.getId(), optionId);
			return this;
		}
	}
}
