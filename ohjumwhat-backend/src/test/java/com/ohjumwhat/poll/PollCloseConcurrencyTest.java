package com.ohjumwhat.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteService;

/**
 * 「지금 마감」과 참여가 겹칠 때. {@code VoteService.vote}는 투표 행을 잠그지 않고 메모리에서만 마감을 판정한 뒤 votes만 쓰고,
 * {@code PollService.close}는 polls.closes_at만 쓴다. 서로 다른 행을 쓰므로 READ COMMITTED에서 충돌 없이 둘 다 커밋된다.
 * 마감 응답(결과 화면)이 보여 준 명단과 그 뒤의 명단이 달라진다(이슈 #143, 결함 기록).
 */
class PollCloseConcurrencyTest extends ConcurrencyTest {

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

	Long orgId;

	Long pollId;

	Long optionA;

	Long optionB;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
		optionA = menuService.add(pollId, kim.getId(), "김치찌개", null).options().getFirst().id();
		optionB = menuService.add(pollId, kim.getId(), "제육볶음", null).options().get(1).id();
		clock.set(2026, 9, 30, 11, 30);
	}

	/**
	 * 결함 기록: 마감 커밋 뒤에 먼저 읽은 참여(메뉴 바꾸기)가 그대로 커밋된다. #143의 수정 이슈에서 「참여가 409 「마감된 투표예요.」로
	 * 끝나고 명단이 바뀌지 않는다」로 뒤집는다.
	 */
	@Test
	void 지금_마감이_커밋된_뒤_먼저_읽은_메뉴_바꾸기가_그대로_커밋된다_현재_동작() throws Exception {
		voteService.vote(pollId, lee.getId(), optionA);
		// lee의 응답 행을 잠가 둔다 → 참여는 투표를 읽고 마감 판정을 통과한 뒤 upsert에서 멈춘다
		RowLock lock = holdLock("select id from votes where poll_id = ? and user_id = ? for update", pollId, lee.getId());
		Future<PollDetailResponse> voter = inThread(() -> voteService.vote(pollId, lee.getId(), optionB));
		awaitLockWait("%insert into votes%");

		PollDetailResponse closed = pollService.close(pollId, kim.getId());
		assertThat(closed.status()).isEqualTo(PollStatus.CLOSED);
		assertThat(voterIds(closed, optionA)).containsExactly(lee.getId());
		assertThat(voterIds(closed, optionB)).isEmpty();

		lock.release();
		PollDetailResponse voteResponse = await(voter);

		// 현재 동작: 참여는 성공했다고 답하고(영속성 컨텍스트의 옛 Poll로 OPEN), 마감 뒤 명단은 바뀌어 있다
		assertThat(voteResponse.status()).isEqualTo(PollStatus.OPEN);
		assertThat(voteResponse.myOptionId()).isEqualTo(optionB);
		PollDetailResponse after = pollService.get(orgId, pollId, kim.getId());
		assertThat(after.status()).isEqualTo(PollStatus.CLOSED);
		assertThat(voterIds(after, optionA)).isEmpty();
		assertThat(voterIds(after, optionB)).containsExactly(lee.getId());
		assertThat(jdbcTemplate.queryForObject("select option_id from votes where poll_id = ? and user_id = ?", Long.class,
				pollId, lee.getId())).isEqualTo(optionB);
	}

	/** 결함 기록: 처음 참여(INSERT)도 같다. 미응답이던 사람이 마감 뒤 명단에 들어온다. */
	@Test
	void 지금_마감이_커밋된_뒤_먼저_읽은_첫_참여가_그대로_커밋된다_현재_동작() throws Exception {
		// 같은 (poll, user) 행을 커밋하지 않은 채 들고 있으면 참여의 INSERT가 고유키 확인에서 기다린다. 풀 때 롤백한다.
		RowLock lock = holdUncommitted("insert into votes (poll_id, user_id, option_id) values (?, ?, ?)", false, pollId,
				lee.getId(), optionA);
		Future<PollDetailResponse> voter = inThread(() -> voteService.vote(pollId, lee.getId(), optionB));
		awaitLockWait("%insert into votes%");

		PollDetailResponse closed = pollService.close(pollId, kim.getId());
		assertThat(closed.nonRespondents()).extracting(p -> p.userId()).containsExactlyInAnyOrder(kim.getId(), lee.getId());

		lock.release();
		await(voter);

		PollDetailResponse after = pollService.get(orgId, pollId, kim.getId());
		assertThat(after.status()).isEqualTo(PollStatus.CLOSED);
		assertThat(voterIds(after, optionB)).containsExactly(lee.getId());
		assertThat(after.nonRespondents()).extracting(p -> p.userId()).containsExactly(kim.getId());
	}

	/** 비교: 마감이 먼저 커밋된 뒤에 읽기 시작한 참여는 409로 막힌다(겹치지 않으면 정상). */
	@Test
	void 마감된_뒤에_읽은_참여는_막힌다() throws Exception {
		pollService.close(pollId, kim.getId());

		Future<PollDetailResponse> voter = inThread(() -> voteService.vote(pollId, lee.getId(), optionB));

		assertThat(awaitFailure(voter)).hasMessage("마감된 투표예요.");
		assertThat(jdbcTemplate.queryForObject("select count(*) from votes", Long.class)).isZero();
	}

	private static List<Long> voterIds(PollDetailResponse response, Long optionId) {
		return response.options().stream()
			.filter(o -> o.id().equals(optionId))
			.findFirst()
			.orElseThrow()
			.voters()
			.stream()
			.map(p -> p.userId())
			.toList();
	}
}
