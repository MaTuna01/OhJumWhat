package com.ohjumwhat.admin;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.TestImages;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.schedule.PollSchedule;
import com.ohjumwhat.schedule.PollScheduleRepository;
import com.ohjumwhat.user.BlockedAccountException;
import com.ohjumwhat.user.BlockedAccountRepository;
import com.ohjumwhat.user.ProfilePhotoService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.user.UserService;
import com.ohjumwhat.vote.VoteService;

class AdminIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserService userService;

	@Autowired
	BlockedAccountRepository blockedAccountRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	PollRepository pollRepository;

	@Autowired
	MenuService menuService;

	@Autowired
	VoteService voteService;

	@Autowired
	PollScheduleRepository scheduleRepository;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	@Autowired
	ProfilePhotoService profilePhotoService;

	User admin;

	User kim;

	User lee;

	/** 김철수·이영희가 함께 있는 조직 */
	Long devOrgId;

	/** 이영희 혼자 있는 조직 */
	Long soloOrgId;

	Long pollId;

	/** 이영희가 올리고 이영희가 참여한 메뉴 */
	Long leeMenuId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0); // 수요일 오전 11:00 (한국 시간)
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));

		OrganizationResponse dev = organizationService.create(kim.getId(), "개발팀");
		devOrgId = dev.id();
		inviteService.join(dev.inviteToken(), lee.getId());
		soloOrgId = organizationService.create(lee.getId(), "혼자팀").id();

		pollId = pollService.create(devOrgId, kim.getId(), new PollRequest("점심", "11:50")).id();
		menuService.add(pollId, kim.getId(), "김치찌개", null);
		PollDetailResponse detail = menuService.add(pollId, lee.getId(), "마라탕", null);
		leeMenuId = detail.options().get(1).id();
		voteService.vote(pollId, lee.getId(), leeMenuId);
	}

	@Test
	void 관리자_API는_로그인하지_않으면_401_일반_회원은_403이다() throws Exception {
		mockMvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/stats").with(loginAs(kim)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("권한이 없어요."));
		mockMvc.perform(get("/api/admin/stats").with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userCount").value(3))
			.andExpect(jsonPath("$.organizationCount").value(2))
			.andExpect(jsonPath("$.todayPollCount").value(1))
			.andExpect(jsonPath("$.openPollCount").value(1));
	}

	@Test
	void 관리자_지정은_다시_로그인하지_않아도_다음_요청부터_반영된다() throws Exception {
		mockMvc.perform(get("/api/admin/users").with(loginAs(kim))).andExpect(status().isForbidden());

		kim.promote();
		userRepository.save(kim);

		mockMvc.perform(get("/api/admin/users").with(loginAs(kim))).andExpect(status().isOk());
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.admin").value(true));
	}

	@Test
	void 회원_목록은_별명과_구글_이름을_같이_보여주고_별명으로도_찾는다() throws Exception {
		userService.changeNickname(lee.getId(), "점심요정");

		mockMvc.perform(get("/api/admin/users").param("q", "요정").with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("점심요정"))
			.andExpect(jsonPath("$[0].googleName").value("이영희"));
		mockMvc.perform(get("/api/admin/users").param("q", "이영").with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void 회원을_검색하고_상세를_본다() throws Exception {
		mockMvc.perform(get("/api/admin/users").param("q", "이영").with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].email").value("lee@example.com"))
			.andExpect(jsonPath("$[0].role").value("USER"))
			.andExpect(jsonPath("$[0].organizationCount").value(2));

		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.name").value("이영희"))
			.andExpect(jsonPath("$.organizations[*].name", contains("개발팀", "혼자팀")))
			.andExpect(jsonPath("$.activity.pollsCreated").value(0))
			.andExpect(jsonPath("$.activity.menusAdded").value(1))
			.andExpect(jsonPath("$.activity.responses").value(1))
			.andExpect(jsonPath("$.lastAccessAt").value(nullValue()));
	}

	@Test
	void 강제_탈퇴하면_조직에서_빠지고_응답은_지워지고_메뉴는_남고_세션이_끝나고_재가입이_막힌다() throws Exception {
		createSession(sessionRepository, "sub-lee");
		assertThat(countSessions("sub-lee")).isEqualTo(1);
		profilePhotoService.upload(lee.getId(), TestImages.jpeg(512, 512));
		assertThat(photoFiles()).hasSize(1);

		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		assertThat(userRepository.findById(lee.getId())).isEmpty();
		assertThat(countSessions("sub-lee")).isZero();
		// 올린 프로필 사진 파일도 지운다.
		assertThat(photoFiles()).isEmpty();
		// 다른 멤버가 있는 조직은 남고, 혼자였던 조직은 삭제된다.
		assertThat(jdbcTemplate.queryForObject("select count(*) from organizations where id = ?", Long.class,
				soloOrgId)).isZero();
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/members").with(loginAs(kim)))
			.andExpect(jsonPath("$[*].name", contains("김철수")));
		// 메뉴는 "탈퇴한 사용자" 것으로 남고, 그 사람의 응답은 사라진다.
		mockMvc.perform(get("/api/orgs/" + devOrgId + "/polls/" + pollId).with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[1].name").value("마라탕"))
			.andExpect(jsonPath("$.options[1].createdBy").value(nullValue()))
			.andExpect(jsonPath("$.options[1].voters", hasSize(0)));
		// 작성자가 없는 메뉴는 아무도 "내가 추가한 메뉴"로 지울 수 없다(500이 아니라 403).
		mockMvc.perform(delete("/api/polls/" + pollId + "/options/" + leeMenuId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isForbidden());
		// 같은 구글 계정으로는 다시 가입할 수 없다.
		assertThat(blockedAccountRepository.findByGoogleSub("sub-lee")).isPresent();
		assertThatThrownBy(() -> userService.login("sub-lee", "lee@example.com", true, "이영희", null))
			.isInstanceOf(BlockedAccountException.class);
		assertThat(userRepository.count()).isEqualTo(2);
	}

	@Test
	void 올린_프로필_사진을_지우면_구글_사진으로_돌아가고_파일도_지워진다() throws Exception {
		profilePhotoService.upload(lee.getId(), TestImages.jpeg(512, 512));
		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.customPhoto").value(true));
		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/photo").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/photo").with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		assertThat(photoFiles()).isEmpty();
		mockMvc.perform(get("/api/admin/users/" + lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.customPhoto").value(false))
			.andExpect(jsonPath("$.user.profileImageUrl").value(nullValue()));
		// 올린 사진이 없어도 그대로 성공하고, 없는 회원은 404
		mockMvc.perform(delete("/api/admin/users/" + lee.getId() + "/photo").with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/admin/users/999999/photo").with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 차단을_풀면_같은_구글_계정으로_새로_가입할_수_있다() throws Exception {
		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()));
		Long blockId = blockedAccountRepository.findByGoogleSub("sub-lee").orElseThrow().getId();
		mockMvc.perform(get("/api/admin/blocks").with(loginAs(admin)))
			.andExpect(jsonPath("$[0].email").value("lee@example.com"))
			.andExpect(jsonPath("$[0].blockedByName").value("관리자"));

		mockMvc.perform(delete("/api/admin/blocks/" + blockId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		User rejoined = userService.login("sub-lee", "lee@example.com", true, "이영희", null);
		assertThat(rejoined.getId()).isNotEqualTo(lee.getId());
	}

	@Test
	void 자기_자신이나_다른_관리자는_탈퇴시킬_수_없고_없는_회원은_404() throws Exception {
		mockMvc.perform(delete("/api/admin/users/" + admin.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isConflict());
		kim.promote();
		userRepository.save(kim);
		mockMvc.perform(delete("/api/admin/users/" + kim.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isConflict());

		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void 조직_목록과_상세를_보고_조직을_삭제한다() throws Exception {
		scheduleRepository.save(new PollSchedule(devOrgId, "점심", 31, LocalTime.of(11, 0), LocalTime.of(11, 50)));

		mockMvc.perform(get("/api/admin/orgs").param("q", "개발").with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].memberCount").value(2))
			.andExpect(jsonPath("$[0].pollCount").value(1))
			.andExpect(jsonPath("$[0].lastPollDate").value("2026-09-30"));
		mockMvc.perform(get("/api/admin/orgs/" + devOrgId).with(loginAs(admin)))
			.andExpect(jsonPath("$.organization.name").value("개발팀"))
			.andExpect(jsonPath("$.members[*].name", contains("김철수", "이영희")))
			.andExpect(jsonPath("$.polls[0].title").value("점심"))
			.andExpect(jsonPath("$.polls[0].status").value("OPEN"))
			.andExpect(jsonPath("$.polls[0].optionCount").value(2))
			.andExpect(jsonPath("$.polls[0].responseCount").value(1))
			.andExpect(jsonPath("$.schedules[0].name").value("점심"));

		mockMvc.perform(delete("/api/admin/orgs/" + devOrgId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		assertThat(pollRepository.count()).isZero();
		assertThat(scheduleRepository.count()).isZero();
		mockMvc.perform(get("/api/admin/orgs/" + devOrgId).with(loginAs(admin))).andExpect(status().isNotFound());
	}

	@Test
	void 멤버를_내보내고_마지막_멤버였다면_조직이_삭제된다() throws Exception {
		mockMvc.perform(delete("/api/admin/orgs/" + devOrgId + "/members/" + lee.getId()).with(loginAs(admin))
			.with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organizationDeleted").value(false));
		mockMvc.perform(delete("/api/admin/orgs/" + devOrgId + "/members/" + lee.getId()).with(loginAs(admin))
			.with(xsrf()))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/admin/orgs/" + soloOrgId + "/members/" + lee.getId()).with(loginAs(admin))
			.with(xsrf()))
			.andExpect(jsonPath("$.organizationDeleted").value(true));
	}

	@Test
	void 참여자가_있는_메뉴도_강제로_지우면_그_사람은_미응답이_된다() throws Exception {
		mockMvc.perform(get("/api/admin/polls/" + pollId).with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options", hasSize(2)));

		mockMvc.perform(delete("/api/admin/menu-options/" + leeMenuId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[*].name", contains("김치찌개")))
			.andExpect(jsonPath("$.nonRespondents[*].name", contains("김철수", "이영희")));
	}

	@Test
	void 투표를_지우고_정기_투표면_규칙도_함께_지울_수_있다() throws Exception {
		PollSchedule schedule = scheduleRepository
			.save(new PollSchedule(devOrgId, "점심", 31, LocalTime.of(11, 0), LocalTime.of(11, 50)));
		Poll scheduled = pollRepository.save(Poll.scheduled(devOrgId, schedule.getId(), "점심",
				LocalDate.of(2026, 9, 30), clock.instant(), clock.instant().plusSeconds(3000)));

		mockMvc.perform(delete("/api/admin/polls/" + pollId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(scheduleRepository.count()).isEqualTo(1);

		mockMvc.perform(delete("/api/admin/polls/" + scheduled.getId()).param("withSchedule", "true")
			.with(loginAs(admin))
			.with(xsrf())).andExpect(status().isNoContent());
		assertThat(pollRepository.count()).isZero();
		assertThat(scheduleRepository.count()).isZero();
	}

	@Test
	void 정기_투표_규칙을_지운다() throws Exception {
		PollSchedule schedule = scheduleRepository
			.save(new PollSchedule(devOrgId, "점심", 31, LocalTime.of(11, 0), LocalTime.of(11, 50)));

		mockMvc.perform(delete("/api/admin/schedules/" + schedule.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/admin/schedules/" + schedule.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound());
	}

	/** 로그인 세션처럼 principal 이름(google sub)이 붙은 실제 세션을 DB에 만든다. */
	private static <S extends Session> void createSession(FindByIndexNameSessionRepository<S> repository,
			String principalName) {
		S session = repository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
		repository.save(session);
	}

	private long countSessions(String principalName) {
		return jdbcTemplate.queryForObject("select count(*) from spring_session where principal_name = ?", Long.class,
				principalName);
	}
}
