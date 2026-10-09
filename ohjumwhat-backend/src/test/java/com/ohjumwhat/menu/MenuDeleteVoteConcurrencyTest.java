package com.ohjumwhat.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteService;

/**
 * 메뉴 삭제(참여자 0명 확인 뒤 DELETE)와 그 메뉴 참여(votes INSERT, FK가 menu_options 행에 KEY SHARE)가 겹칠 때.
 * {@code votes.option_id} FK는 NO ACTION이라 문장 끝에 검사된다. 어느 순서든 DB가 한쪽을 막아 「메뉴는 없는데 응답이 남는」 상태는
 * 생기지 않는다(안전 확인, 이슈 #143). 막힌 쪽의 문구가 「다른 사람의 변경과 겹쳤어요」인 것만 아쉽다.
 */
class MenuDeleteVoteConcurrencyTest extends ConcurrencyTest {

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

	User kim;

	User lee;

	Long pollId;

	Long optionA;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		inviteService.join(org.inviteToken(), lee.getId());
		pollId = pollService.create(org.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
		optionA = menuService.add(pollId, kim.getId(), "김치찌개", null).options().getFirst().id();
	}

	/** 참여가 먼저 잠금을 기다렸으면 참여가 커밋되고, 삭제는 「참여자 0명」을 통과했어도 DELETE 뒤 FK 검사에서 막힌다. */
	@Test
	void 참여가_먼저면_삭제가_FK에_막힌다() throws Exception {
		RowLock lock = holdLock("select id from menu_options where id = ? for update", optionA);
		Future<PollDetailResponse> voter = inThread(() -> voteService.vote(pollId, lee.getId(), optionA));
		awaitLockWait("%insert into votes%");
		Future<PollDetailResponse> deleter = inThread(() -> menuService.delete(pollId, optionA, kim.getId()));
		awaitLockWait("%delete from menu_options%");

		lock.release();

		assertThat(await(voter).myOptionId()).isEqualTo(optionA);
		assertThat(awaitFailure(deleter)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbcTemplate.queryForObject("select count(*) from menu_options", Long.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from votes", Long.class)).isEqualTo(1);
	}

	/** 삭제가 먼저면 삭제가 커밋되고, 참여는 FK 검사에서 막힌다. */
	@Test
	void 삭제가_먼저면_참여가_FK에_막힌다() throws Exception {
		RowLock lock = holdLock("select id from menu_options where id = ? for update", optionA);
		Future<PollDetailResponse> deleter = inThread(() -> menuService.delete(pollId, optionA, kim.getId()));
		awaitLockWait("%delete from menu_options%");
		Future<PollDetailResponse> voter = inThread(() -> voteService.vote(pollId, lee.getId(), optionA));
		awaitLockWait("%insert into votes%");

		lock.release();

		assertThat(await(deleter).options()).isEmpty();
		assertThat(awaitFailure(voter)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbcTemplate.queryForObject("select count(*) from menu_options", Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from votes", Long.class)).isZero();
	}
}
