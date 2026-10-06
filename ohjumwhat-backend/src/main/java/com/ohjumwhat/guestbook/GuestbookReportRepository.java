package com.ohjumwhat.guestbook;

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

public interface GuestbookReportRepository extends JpaRepository<GuestbookReport, Long> {

	boolean existsByEntryId(Long entryId);

	@Query("select r.entryId from GuestbookReport r where r.entryId in :entryIds")
	List<Long> findReportedEntryIds(Collection<Long> entryIds);

	@Query("select count(r) from GuestbookReport r where r.resolvedAt is null")
	long countOpen();

	/** 관리자 처리: 두 관리자가 같은 신고를 함께 처리해도 한 번만 처리되도록(경고가 두 번 가지 않게) 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from GuestbookReport r where r.id = :id")
	Optional<GuestbookReport> findByIdForUpdate(Long id);

	/** 관리자 콘솔: 신고 목록(최근순). openOnly면 처리 전만. 제한·삭제된 글도 원문. 이름은 별명(없으면 구글 이름) */
	@Query("""
			select new com.ohjumwhat.guestbook.GuestbookReportResponse(r.id, r.createdAt, r.reason, r.resolution,
				r.resolvedAt, coalesce(a.nickname, a.name), e.id, e.body, e.createdAt, e.deletedAt, e.restrictedAt,
				o.id, coalesce(o.nickname, o.name), o.email, w.id, coalesce(w.nickname, w.name), w.email)
			from GuestbookReport r
			join GuestbookEntry e on e.id = r.entryId
			left join com.ohjumwhat.user.User o on o.id = e.ownerId
			left join com.ohjumwhat.user.User w on w.id = e.authorId
			left join com.ohjumwhat.user.User a on a.id = r.resolvedBy
			where :openOnly = false or r.resolvedAt is null
			order by r.id desc""")
	List<GuestbookReportResponse> findForAdmin(boolean openOnly, Pageable pageable);

	/**
	 * 강제 탈퇴 1단계: 그 사람이 쓴 글 중 처리 전 신고가 있는 글을 제한한다(지운 글 포함). 신고를 처리하기 전에 불러야
	 * 어느 글인지 찾을 수 있다. 제한한 글 수를 돌려준다.
	 */
	@Modifying
	@Query("""
			update GuestbookEntry e set e.restrictedAt = :now
			where e.authorId = :authorId and e.restrictedAt is null
				and e.id in (select r.entryId from GuestbookReport r where r.resolvedAt is null)""")
	int restrictOpenlyReportedEntriesOf(Long authorId, Instant now);

	/**
	 * 강제 탈퇴 2단계: 그 사람이 쓴 글의 처리 전 신고를 「글 제한」으로 처리한다(회원을 지우면 쓴 사람을 알 수 없으므로 먼저).
	 * 처리한 신고 수를 돌려준다.
	 */
	@Modifying
	@Query("""
			update GuestbookReport r set r.resolution = :resolution, r.resolvedAt = :now, r.resolvedBy = :adminId
			where r.resolvedAt is null
				and r.entryId in (select e.id from GuestbookEntry e where e.authorId = :authorId)""")
	int resolveOpenAgainstAuthor(Long authorId, GuestbookReportResolution resolution, Long adminId, Instant now);
}
