package com.ohjumwhat.report;

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

/** 사람 신고 조회·변경. 「처리 전」 = resolved_at IS NULL(V22의 부분 인덱스와 같은 조건) */
public interface ProfileReportRepository extends JpaRepository<ProfileReport, Long> {

	/**
	 * 신고한다. 신고된 사람의 지금 프로필(이름은 별명, 없으면 구글 이름)을 회원 행에서 바로 골라 사본으로 둔다. 같은
	 * (신고한 사람, 신고된 사람)의 처리 전 신고가 이미 있으면(동시에 보낸 요청 포함) DB의 ON CONFLICT로 아무것도 하지 않는다.
	 *
	 * @return 만든 행 수(이미 처리 전 신고가 있거나 회원이 없으면 0)
	 */
	@Modifying
	@Query(value = """
			insert into profile_reports (reporter_id, target_id, reason, detail, target_name, target_bio, target_food_tags,
				target_hobbies, target_job_title, created_at)
			select :reporterId, u.id, :reason, cast(:detail as varchar), coalesce(u.nickname, u.name), u.bio, u.food_tags,
				u.hobbies, u.job_title, :now
			from users u
			where u.id = :targetId
			on conflict (reporter_id, target_id) where resolved_at is null do nothing""", nativeQuery = true)
	int insertOpen(Long reporterId, Long targetId, String reason, String detail, Instant now);

	/** 관리자 처리: 두 관리자가 같은 신고를 함께 처리하거나 제재·강제 탈퇴와 겹쳐도 한 번만 처리되도록 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ProfileReport r where r.id = :id")
	Optional<ProfileReport> findByIdForUpdate(Long id);

	/** 제재·강제 탈퇴: 그 사람에 대한 처리 전 신고를 잠근다(「문제 없음」 처리와 겹치지 않게, 잠금 순서를 맞추려고 ID순). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ProfileReport r where r.targetId = :targetId and r.resolvedAt is null order by r.id")
	List<ProfileReport> findOpenAgainstForUpdate(Long targetId);

	@Query("select count(r) from ProfileReport r where r.resolvedAt is null")
	long countOpen();

	/** 관리자 회원 상세: 그 사람에 대한 처리 전 신고 수(제재하면 함께 처리된다) */
	@Query("select count(r) from ProfileReport r where r.targetId = :targetId and r.resolvedAt is null")
	long countOpenAgainst(Long targetId);

	/** 신고한 사람이 아직 결과 창에서 보지 않은 처리 결과(처리 시각 오래된 순) */
	@Query("""
			select r from ProfileReport r
			where r.reporterId = :reporterId and r.resolvedAt is not null and r.resultSeenAt is null
			order by r.resolvedAt, r.id""")
	List<ProfileReport> findUnseenResults(Long reporterId, Pageable pageable);

	/** 결과 창을 봤다: 내가 신고했고 처리됐고 아직 보지 않은 것만 now로. 바뀐 행 수를 돌려준다. */
	@Modifying
	@Query("""
			update ProfileReport r set r.resultSeenAt = :now
			where r.reporterId = :reporterId and r.id in :ids and r.resolvedAt is not null and r.resultSeenAt is null""")
	int markResultsSeen(Long reporterId, Collection<Long> ids, Instant now);

	/** 관리자 콘솔의 신고 한 줄 */
	@Query("""
			select new com.ohjumwhat.report.ProfileReportRow$Source(r, t.id, coalesce(t.nickname, t.name), t.email,
				t.photoKey, t.profileImageUrl, t.role, t.bio, t.foodTags, t.hobbies, t.jobTitle, p.id,
				coalesce(p.nickname, p.name), p.email, coalesce(a.nickname, a.name))
			from ProfileReport r
			left join com.ohjumwhat.user.User t on t.id = r.targetId
			left join com.ohjumwhat.user.User p on p.id = r.reporterId
			left join com.ohjumwhat.user.User a on a.id = r.resolvedBy
			where r.id = :id""")
	Optional<ProfileReportRow.Source> findRow(Long id);

	/** 관리자 콘솔의 신고 목록(최신순, 개수는 pageable로 자른다). openOnly면 처리 전만 */
	@Query("""
			select new com.ohjumwhat.report.ProfileReportRow$Source(r, t.id, coalesce(t.nickname, t.name), t.email,
				t.photoKey, t.profileImageUrl, t.role, t.bio, t.foodTags, t.hobbies, t.jobTitle, p.id,
				coalesce(p.nickname, p.name), p.email, coalesce(a.nickname, a.name))
			from ProfileReport r
			left join com.ohjumwhat.user.User t on t.id = r.targetId
			left join com.ohjumwhat.user.User p on p.id = r.reporterId
			left join com.ohjumwhat.user.User a on a.id = r.resolvedBy
			where :openOnly = false or r.resolvedAt is null
			order by r.id desc""")
	List<ProfileReportRow.Source> findRows(boolean openOnly, Pageable pageable);
}
