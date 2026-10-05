package com.ohjumwhat.letter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LetterReportRepository extends JpaRepository<LetterReport, Long> {

	boolean existsByLetterId(Long letterId);

	@Query("select r.letterId from LetterReport r where r.letterId in :letterIds")
	List<Long> findReportedLetterIds(Collection<Long> letterIds);

	@Query("select count(r) from LetterReport r where r.resolvedAt is null")
	long countOpen();

	/** 관리자 콘솔: 신고 목록(최근순). openOnly면 처리 전만. 이름은 별명(없으면 구글 이름) */
	@Query("""
			select new com.ohjumwhat.letter.LetterReportResponse(r.id, r.createdAt, r.reason, r.resolvedAt,
				coalesce(a.nickname, a.name), l.id, l.body, l.anonymous, l.createdAt, o.id, o.name,
				s.id, coalesce(s.nickname, s.name), s.email, t.id, coalesce(t.nickname, t.name), t.email)
			from LetterReport r
			join Letter l on l.id = r.letterId
			left join com.ohjumwhat.organization.Organization o on o.id = l.organizationId
			left join com.ohjumwhat.user.User s on s.id = l.senderId
			left join com.ohjumwhat.user.User t on t.id = l.recipientId
			left join com.ohjumwhat.user.User a on a.id = r.resolvedBy
			where :openOnly = false or r.resolvedAt is null
			order by r.id desc""")
	List<LetterReportResponse> findForAdmin(boolean openOnly, Pageable pageable);

	/** 강제 탈퇴: 그 사람이 보낸 쪽지의 열린 신고를 처리 완료로 한다(회원을 지우면 보낸 사람이 「탈퇴한 사용자」가 되므로 먼저). */
	@Modifying
	@Query("""
			update LetterReport r set r.resolvedAt = :now, r.resolvedBy = :adminId
			where r.resolvedAt is null and r.letterId in (select l.id from Letter l where l.senderId = :senderId)""")
	int resolveOpenAgainst(Long senderId, Long adminId, Instant now);
}
