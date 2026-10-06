package com.ohjumwhat.guestbook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 방명록 글 조회. 제한된 글의 본문은 쿼리에서 아예 고르지 않아 응답 어디에도 실리지 않는다(쓴 사람의 경고만 예외). */
public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {

	/** 방명록 한 쪽(최신순). 지운 글은 빼고, 제한된 글은 본문 없이 넣는다. 쓴 사람 이름은 별명(없으면 구글 이름) */
	@Query(value = """
			select new com.ohjumwhat.guestbook.GuestbookEntryRow(e.id, u.id, coalesce(u.nickname, u.name), u.photoKey,
				u.profileImageUrl, case when e.restrictedAt is null then e.body else null end, e.createdAt,
				case when e.restrictedAt is null then false else true end)
			from GuestbookEntry e
			left join com.ohjumwhat.user.User u on u.id = e.authorId
			where e.ownerId = :ownerId and e.deletedAt is null
			order by e.id desc""",
			countQuery = "select count(e) from GuestbookEntry e where e.ownerId = :ownerId and e.deletedAt is null")
	Page<GuestbookEntryRow> findPage(Long ownerId, Pageable pageable);

	/** 지우지 않은 글 한 줄(지우기·신고) */
	Optional<GuestbookEntry> findByIdAndDeletedAtIsNull(Long id);

	/** 내 방명록의 새 글 수: 지우지 않은 글 중 since(마지막으로 본 시각)보다 늦게 쓰인 글 */
	@Query("""
			select count(e) from GuestbookEntry e
			where e.ownerId = :ownerId and e.deletedAt is null and e.createdAt > :since""")
	long countNewSince(Long ownerId, Instant since);

	/** 쓴 사람의 경고: since(마지막으로 확인한 시각)보다 늦게 제한된 내 글(지운 글 포함, 제한한 순서). 원문을 함께 준다. */
	@Query("""
			select new com.ohjumwhat.guestbook.GuestbookAlertsResponse$Warning(e.id, o.id, coalesce(o.nickname, o.name),
				o.photoKey, o.profileImageUrl, e.body, e.createdAt, e.restrictedAt)
			from GuestbookEntry e
			left join com.ohjumwhat.user.User o on o.id = e.ownerId
			where e.authorId = :authorId and e.restrictedAt > :since
			order by e.restrictedAt, e.id""")
	List<GuestbookAlertsResponse.Warning> findWarnings(Long authorId, Instant since);
}
