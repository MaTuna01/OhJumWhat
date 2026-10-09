package com.ohjumwhat.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 초대 링크 참여의 동시성. {@code InviteService.join}은 「이미 멤버면 아무것도 안 한다」지만 exists → INSERT 사이가 비어 있어
 * 같은 사람이 동시에 두 번 누르면 두 번째가 UNIQUE 위반 409다(멱등하지 않다).
 *
 * <p>마지막 멤버의 탈퇴(조직 삭제)와 겹칠 때: 탈퇴의 조직 행 잠금은 {@code FOR NO KEY UPDATE}(Hibernate 7)라 참여의 FK 검사(KEY SHARE)와
 * 호환이다. 그래서 참여가 커밋된 뒤에 탈퇴가 세면 조직이 남고(정상), 참여의 INSERT와 커밋 사이에 탈퇴가 세면 조직이 지워지면서 방금 들어간
 * 멤버십도 CASCADE로 사라진다(결함). 탈퇴가 먼저 커밋된 뒤의 INSERT는 FK 위반 409인데, 참여가 조직을 읽은 뒤 INSERT하기 전 창이라
 * 테스트로 강제할 수 없어 분석으로만 둔다(이슈 #143).
 */
class InviteJoinConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	User kim;

	User lee;

	Long orgId;

	String token;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		token = org.inviteToken();
	}

	/** 결함 기록: 같은 사람의 동시 참여(더블 클릭)는 두 번째가 성공 대신 UNIQUE 위반이다. 수정 이슈에서 「둘 다 조직 ID를 돌려준다」로 뒤집는다. */
	@Test
	void 같은_사람이_동시에_참여하면_두_번째는_제약_위반이다_현재_동작() throws Exception {
		// 첫 번째 요청의 INSERT를 커밋하지 않은 채 두면 두 번째의 INSERT가 고유키 확인에서 기다린다
		RowLock first = holdUncommitted("insert into memberships (organization_id, user_id, joined_at) values (?, ?, now())",
				true, orgId, lee.getId());
		Future<Long> second = inThread(() -> inviteService.join(token, lee.getId()));
		awaitLockWait("%insert into memberships%");

		first.release();

		assertThat(awaitFailure(second)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbcTemplate.queryForObject("select count(*) from memberships where user_id = ?", Long.class,
				lee.getId())).isEqualTo(1);
	}

	/**
	 * 결함 기록: 참여의 INSERT가 FK 검사까지 마치고 커밋되기 전에 마지막 멤버가 탈퇴하면, 탈퇴는 멤버를 하나로 세어 조직을 지우고
	 * (조직 삭제는 참여의 KEY SHARE가 풀릴 때까지 기다렸다가) CASCADE가 방금 들어간 멤버십을 지운다. 참여한 사람은 성공 응답을 받지만
	 * 조직은 사라진다. 탈퇴의 FOR NO KEY UPDATE가 FK의 KEY SHARE와 호환이라 생기는 창이다. #143의 수정 이슈에서 「참여가 조직 행을
	 * FOR SHARE(PESSIMISTIC_READ)로 읽어 탈퇴와 직렬화된다(둘 중 하나는 조직이 남거나 참여가 404)」로 뒤집는다.
	 */
	@Test
	void 참여가_커밋되기_전에_마지막_멤버가_탈퇴하면_참여가_조용히_사라진다_현재_동작() throws Exception {
		CountDownLatch commit = new CountDownLatch(1);
		Future<Long> joiner = inTransactionThread(() -> inviteService.join(token, lee.getId()), commit);
		awaitIdleInTransaction("%insert into memberships%");

		Future<LeaveResponse> leaver = inThread(() -> organizationService.leave(orgId, kim.getId()));
		// 탈퇴는 멤버를 하나(kim)로 세고 조직을 지우려다 참여의 KEY SHARE에서 기다린다
		awaitLockWait("%delete from organizations%");

		commit.countDown();

		assertThat(await(joiner)).isEqualTo(orgId);
		assertThat(await(leaver).organizationDeleted()).isTrue();
		assertThat(jdbcTemplate.queryForObject("select count(*) from organizations", Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from memberships where user_id = ?", Long.class,
				lee.getId())).isZero();
	}

	/** 참여가 먼저 커밋되면 탈퇴는 멤버가 둘이라 조직을 남긴다(정상). */
	@Test
	void 참여가_먼저_커밋되면_탈퇴해도_조직이_남는다() throws Exception {
		RowLock lock = holdLock("select id from organizations where id = ? for update", orgId);
		Future<Long> joiner = inThread(() -> inviteService.join(token, lee.getId()));
		awaitLockWait("%insert into memberships%");
		Future<LeaveResponse> leaver = inThread(() -> organizationService.leave(orgId, kim.getId()));
		awaitLockWait("%from organizations%for %update%");

		lock.release();

		assertThat(await(joiner)).isEqualTo(orgId);
		assertThat(await(leaver).organizationDeleted()).isFalse();
		assertThat(jdbcTemplate.queryForList("select user_id from memberships where organization_id = ?", Long.class,
				orgId)).containsExactly(lee.getId());
	}
}
