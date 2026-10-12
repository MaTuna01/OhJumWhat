package com.ohjumwhat.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 투표 수정(마감 시간 뒤로)과 「지금 마감」이 겹칠 때. 둘 다 투표를 잠금 없이 읽고 메모리에서 마감을 판정한 뒤 커밋 때 polls 행 전체를
 * UPDATE한다({@code @Version} 없음). 행 잠금으로 순서만 정해지고 조건이 없어 나중에 커밋한 쪽이 이긴다. 마감이 먼저 커밋되고
 * 수정이 뒤에 커밋되면 closes_at이 미래로 되돌아가 마감된 투표가 다시 열린다(이슈 #143, 결함 기록).
 */
class PollUpdateCloseConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	User kim;

	User lee;

	Long orgId;

	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
		clock.set(2026, 9, 30, 11, 30);
	}

	/**
	 * 결함 기록: 마감(closes_at = 11:30)이 커밋된 뒤 수정(12:30)이 커밋되면 투표가 다시 열린다. 마감한 사람 화면은 결과, 수정한 사람
	 * 화면은 진행 중이다. #143의 수정 이슈에서 「수정이 409 「마감된 투표예요.」로 끝나고 closes_at은 11:30 그대로」로 뒤집는다.
	 */
	@Test
	void 마감이_커밋된_뒤_수정이_커밋되면_마감된_투표가_다시_열린다_현재_동작() throws Exception {
		// polls 행을 잠가 두면 마감과 수정 모두 읽기·판정은 끝내고 커밋의 UPDATE에서 기다린다. 먼저 기다린 마감이 먼저 커밋된다.
		RowLock lock = holdLock("select id from polls where id = ? for update", pollId);
		Future<PollDetailResponse> closer = inThread(() -> pollService.close(pollId, kim.getId()));
		awaitLockWait("%update polls%", 1);
		Future<PollDetailResponse> updater = inThread(() -> pollService.update(pollId, lee.getId(),
				new PollRequest("점심", "12:30")));
		awaitLockWait("%update polls%", 2);

		lock.release();
		PollDetailResponse closed = await(closer);
		PollDetailResponse updated = await(updater);

		assertThat(closed.status()).isEqualTo(PollStatus.CLOSED);
		assertThat(updated.status()).isEqualTo(PollStatus.OPEN);
		// 현재 동작: 나중에 커밋한 수정이 이겨 마감이 되돌아갔다
		assertThat(closesAt()).isEqualTo(Instant.parse("2026-09-30T03:30:00Z"));
		assertThat(pollService.get(orgId, pollId, kim.getId()).status()).isEqualTo(PollStatus.OPEN);
	}

	/** 비교: 수정이 먼저 커밋되고 마감이 뒤에 커밋되면 마감이 남는다(이 순서는 문제없다). */
	@Test
	void 수정이_커밋된_뒤_마감이_커밋되면_마감이_남는다() throws Exception {
		RowLock lock = holdLock("select id from polls where id = ? for update", pollId);
		Future<PollDetailResponse> updater = inThread(() -> pollService.update(pollId, lee.getId(),
				new PollRequest("점심", "12:30")));
		awaitLockWait("%update polls%", 1);
		Future<PollDetailResponse> closer = inThread(() -> pollService.close(pollId, kim.getId()));
		awaitLockWait("%update polls%", 2);

		lock.release();
		await(updater);
		await(closer);

		assertThat(closesAt()).isEqualTo(Instant.parse("2026-09-30T02:30:00Z"));
		assertThat(pollService.get(orgId, pollId, kim.getId()).status()).isEqualTo(PollStatus.CLOSED);
	}

	private Instant closesAt() {
		return jdbcTemplate.queryForObject("select closes_at from polls where id = ?", java.time.OffsetDateTime.class, pollId)
			.toInstant();
	}
}
