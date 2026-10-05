package com.ohjumwhat.letter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 쪽지 조회. 익명 쪽지의 보낸 사람(받은 쪽지)과 숨긴 받는 사람(보낸 쪽지)은 사람 join 조건에서 빼서 아예 고르지 않는다.
 * 그래서 응답 어디에도 그 사람의 id·이름·사진이 실리지 않는다.
 */
public interface LetterRepository extends JpaRepository<Letter, Long> {

	/**
	 * 받은 쪽지함에 보이는 쪽지(쿼리의 :userId가 받은 사람): 지우지 않았고, 같은 범위(익명 여부)로 차단한 보낸 사람이 아니다.
	 * 목록·안 읽은 수·한 통 조회가 함께 쓴다.
	 */
	String VISIBLE_RECEIVED = " l.recipientId = :userId and l.recipientDeletedAt is null"
			+ " and not exists (select b.id from LetterBlock b where b.userId = :userId"
			+ " and b.blockedUserId = l.senderId and b.anonymous = l.anonymous) ";

	String RECEIVED_ROW = """
			select new com.ohjumwhat.letter.LetterRow(l.id, l.organizationId, o.name, u.id, coalesce(u.nickname, u.name),
				u.photoKey, u.profileImageUrl, l.anonymous, l.recipientHidden,
				case when l.senderId is null then false else true end, l.body, l.createdAt, l.readAt, l.replyToId)
			from Letter l
			left join com.ohjumwhat.organization.Organization o on o.id = l.organizationId
			left join com.ohjumwhat.user.User u on u.id = l.senderId and l.anonymous = false
			""";

	String SENT_ROW = """
			select new com.ohjumwhat.letter.LetterRow(l.id, l.organizationId, o.name, u.id, coalesce(u.nickname, u.name),
				u.photoKey, u.profileImageUrl, l.anonymous, l.recipientHidden, true, l.body, l.createdAt, l.readAt,
				l.replyToId)
			from Letter l
			left join com.ohjumwhat.organization.Organization o on o.id = l.organizationId
			left join com.ohjumwhat.user.User u on u.id = l.recipientId and l.recipientHidden = false
			""";

	@Query(RECEIVED_ROW + "where l.id < :before and" + VISIBLE_RECEIVED + "order by l.id desc")
	List<LetterRow> findReceived(Long userId, Long before, Pageable pageable);

	@Query(SENT_ROW + "where l.senderId = :userId and l.senderDeletedAt is null and l.id < :before order by l.id desc")
	List<LetterRow> findSent(Long userId, Long before, Pageable pageable);

	@Query(SENT_ROW + "where l.id = :id")
	Optional<LetterRow> findSentRow(Long id);

	@Query("select count(l) from Letter l where l.readAt is null and" + VISIBLE_RECEIVED)
	long countUnread(Long userId);

	/** 내 받은 쪽지함에 보이는 쪽지 한 통(읽기·답장·차단·신고·지우기) */
	@Query("select l from Letter l where l.id = :id and" + VISIBLE_RECEIVED)
	Optional<Letter> findVisibleReceived(Long id, Long userId);

	/** 내가 받았고 지우지 않은 쪽지 한 통(차단으로 숨긴 것 포함). 차단을 다시 눌러도 그대로이게 차단에 쓴다. */
	@Query("select l from Letter l where l.id = :id and l.recipientId = :userId and l.recipientDeletedAt is null")
	Optional<Letter> findReceivedIncludingBlocked(Long id, Long userId);

	/** 내 보낸 쪽지함에 있는 쪽지 한 통(지우기) */
	@Query("select l from Letter l where l.id = :id and l.senderId = :userId and l.senderDeletedAt is null")
	Optional<Letter> findVisibleSent(Long id, Long userId);

	/** 답장 원문의 첫 줄을 만들려고 본문을 읽는다([id, body]). */
	@Query("select l.id, l.body from Letter l where l.id in :ids")
	List<Object[]> findBodies(Collection<Long> ids);
}
