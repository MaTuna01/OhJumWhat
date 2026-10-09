package com.ohjumwhat.admin;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteService;

/**
 * 강제 탈퇴(회원 행 잠금 → 조직 행 잠금 → 마지막 멤버면 조직 삭제 CASCADE)와 그 사람의 참여·조직 탈퇴가 겹칠 때.
 *
 * <p>계획 단계에서는 「참여의 FK 검사(users 행 KEY SHARE)가 탈퇴의 회원 잠금에 막히고, 탈퇴의 조직 삭제 CASCADE가 참여의 polls KEY SHARE에
 * 막혀 교착한다」고 봤지만, Hibernate 7의 PostgreSQL 방언은 {@code PESSIMISTIC_WRITE}를 {@code FOR NO KEY UPDATE}로 내고 이는
 * KEY SHARE와 호환이라 참여는 기다리지 않는다. 교착은 없고, 탈퇴가 그 참여를 지우고 끝난다(안전 확인, 이슈 #143).
 */
class WithdrawConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	PollService pollService;

	@Autowired
	MenuService menuService;

	@Autowired
	VoteService voteService;

	@Autowired
	AdminService adminService;

	User admin;

	User lee;

	Long orgId;

	Long pollId;

	Long optionA;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		admin = userRepository.save(admin);
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(lee.getId(), "개발팀");
		orgId = org.id();
		pollId = pollService.create(orgId, lee.getId(), new PollRequest("점심", "11:50")).id();
		optionA = menuService.add(pollId, lee.getId(), "김치찌개", null).options().getFirst().id();
	}

	/** 회원 행을 잠근 탈퇴가 조직 행에서 기다리는 동안에도 첫 참여는 막히지 않고 커밋된다. 탈퇴는 이어서 그 응답을 지우고 회원을 지운다. */
	@Test
	void 마지막_멤버의_첫_참여는_강제_탈퇴를_기다리지_않고_탈퇴가_그_응답을_지운다() throws Exception {
		RowLock lock = holdLock("select id from organizations where id = ? for update", orgId);
		Future<MockHttpServletResponse> withdrawer = inThread(() -> mockMvc
			.perform(delete("/api/admin/users/{userId}", lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andReturn()
			.getResponse());
		awaitLockWait("%from organizations%for %update%");

		// 참여의 FK 검사(users KEY SHARE)는 탈퇴의 FOR NO KEY UPDATE와 호환이라 바로 커밋된다
		PollDetailResponse voteResponse = await(inThread(() -> voteService.vote(pollId, lee.getId(), optionA)));
		assertThat(voteResponse.myOptionId()).isEqualTo(optionA);
		assertThat(withdrawer.isDone()).isFalse();
		assertThat(jdbcTemplate.queryForObject("select count(*) from votes", Long.class)).isEqualTo(1);

		lock.release();

		assertThat(await(withdrawer).getStatus()).isEqualTo(204);
		assertThat(userRepository.findById(lee.getId())).isEmpty();
		assertThat(jdbcTemplate.queryForObject("select count(*) from organizations", Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from votes", Long.class)).isZero();
	}

	/** 조직 탈퇴(leave)는 조직 행만 잠가 강제 탈퇴와 잠금 순서가 같다. 교착 없이 뒤에 온 쪽이 404로 끝난다. */
	@Test
	void 조직_탈퇴와_강제_탈퇴는_교착하지_않는다() throws Exception {
		RowLock lock = holdLock("select id from organizations where id = ? for update", orgId);
		Future<Void> withdrawer = inThread(() -> adminService.withdraw(admin.getId(), lee.getId()));
		awaitLockWait("%from organizations%for %update%", 1);
		Future<?> leaver = inThread(() -> organizationService.leave(orgId, lee.getId()));
		awaitLockWait("%from organizations%for %update%", 2);

		lock.release();

		await(withdrawer);
		assertThat(awaitFailure(leaver)).isInstanceOf(ApiException.class);
		assertThat(userRepository.findById(lee.getId())).isEmpty();
		assertThat(jdbcTemplate.queryForObject("select count(*) from organizations", Long.class)).isZero();
	}
}
