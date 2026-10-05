package com.ohjumwhat.letter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LetterBlockRepository extends JpaRepository<LetterBlock, Long> {

	boolean existsByUserIdAndBlockedUserIdAndAnonymous(Long userId, Long blockedUserId, boolean anonymous);

	/** 익명 쪽지 한 통을 차단했는지 */
	boolean existsByUserIdAndLetterIdAndAnonymousTrue(Long userId, Long letterId);

	boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

	Optional<LetterBlock> findByIdAndUserId(Long id, Long userId);

	/** 내 차단 목록(최근순). 익명 차단은 차단한 사람을 고르지 않는다. */
	@Query("""
			select new com.ohjumwhat.letter.LetterBlockRow(b.id, b.anonymous, u.id, coalesce(u.nickname, u.name),
				u.photoKey, u.profileImageUrl, l.body, b.createdAt)
			from LetterBlock b
			left join com.ohjumwhat.user.User u on u.id = b.blockedUserId and b.anonymous = false
			left join Letter l on l.id = b.letterId
			where b.userId = :userId
			order by b.id desc""")
	List<LetterBlockRow> findRows(Long userId);
}
