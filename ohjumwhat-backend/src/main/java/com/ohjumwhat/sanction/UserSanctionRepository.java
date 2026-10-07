package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 제재 조회·변경. 「활성」 = 제한이 있고(array_length > 0, PostgreSQL에서는 cardinality) 해제되지 않았고 끝나는 시각 전.
 * 이 조건은 아래 쿼리들과 UserSanction.status가 같다.
 */
public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

	/** 요청마다 확인하는 그 회원의 활성 제재(오래된 순). 한 쿼리다. */
	@Query("""
			select s from UserSanction s
			where s.userId = :userId and array_length(s.restrictions) > 0 and s.liftedAt is null
				and (s.endsAt is null or s.endsAt > :now)
			order by s.id""")
	List<UserSanction> findActive(Long userId, Instant now);

	/** 관리자 개요: 지금 활성 제재가 있는 회원 수 */
	@Query("""
			select count(distinct s.userId) from UserSanction s
			where array_length(s.restrictions) > 0 and s.liftedAt is null and (s.endsAt is null or s.endsAt > :now)""")
	long countRestrictedUsers(Instant now);

	/** 관리자 회원 목록·상세: 그중 지금 활성 제재가 있는 회원 */
	@Query("""
			select distinct s.userId from UserSanction s
			where s.userId in :userIds and array_length(s.restrictions) > 0 and s.liftedAt is null
				and (s.endsAt is null or s.endsAt > :now)""")
	List<Long> findRestrictedUserIds(Collection<Long> userIds, Instant now);

	/** 본인이 아직 안내 창에서 보지 않은 제재(오래된 순) */
	@Query("select s from UserSanction s where s.userId = :userId and s.seenAt is null order by s.id")
	List<UserSanction> findUnseen(Long userId, Pageable pageable);

	/** 안내 창을 봤다: 내 것이고 아직 보지 않은 것만 now로. 바뀐 행 수를 돌려준다. */
	@Modifying
	@Query("""
			update UserSanction s set s.seenAt = :now
			where s.userId = :userId and s.id in :ids and s.seenAt is null""")
	int markSeen(Long userId, Collection<Long> ids, Instant now);

	/** 해제: 두 관리자가 같은 제재를 함께 해제해도 한 번만 해제되도록 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from UserSanction s where s.id = :id")
	Optional<UserSanction> findByIdForUpdate(Long id);

	/** 관리자 콘솔의 제재 한 줄 */
	@Query("""
			select new com.ohjumwhat.sanction.SanctionRow$Source(s, coalesce(u.nickname, u.name), u.email, u.photoKey,
				u.profileImageUrl, coalesce(c.nickname, c.name), coalesce(l.nickname, l.name))
			from UserSanction s
			join com.ohjumwhat.user.User u on u.id = s.userId
			left join com.ohjumwhat.user.User c on c.id = s.createdBy
			left join com.ohjumwhat.user.User l on l.id = s.liftedBy
			where s.id = :id""")
	Optional<SanctionRow.Source> findRow(Long id);

	/** 회원 상세의 제재 기록(최신순, 개수는 pageable로 자른다) */
	@Query("""
			select new com.ohjumwhat.sanction.SanctionRow$Source(s, coalesce(u.nickname, u.name), u.email, u.photoKey,
				u.profileImageUrl, coalesce(c.nickname, c.name), coalesce(l.nickname, l.name))
			from UserSanction s
			join com.ohjumwhat.user.User u on u.id = s.userId
			left join com.ohjumwhat.user.User c on c.id = s.createdBy
			left join com.ohjumwhat.user.User l on l.id = s.liftedBy
			where s.userId = :userId
			order by s.id desc""")
	List<SanctionRow.Source> findRowsOfUser(Long userId, Pageable pageable);

	/** 관리자 「제재」 탭(최신순). activeOnly면 활성 제재만 */
	@Query("""
			select new com.ohjumwhat.sanction.SanctionRow$Source(s, coalesce(u.nickname, u.name), u.email, u.photoKey,
				u.profileImageUrl, coalesce(c.nickname, c.name), coalesce(l.nickname, l.name))
			from UserSanction s
			join com.ohjumwhat.user.User u on u.id = s.userId
			left join com.ohjumwhat.user.User c on c.id = s.createdBy
			left join com.ohjumwhat.user.User l on l.id = s.liftedBy
			where :activeOnly = false or (array_length(s.restrictions) > 0 and s.liftedAt is null
				and (s.endsAt is null or s.endsAt > :now))
			order by s.id desc""")
	List<SanctionRow.Source> findRows(boolean activeOnly, Instant now, Pageable pageable);
}
