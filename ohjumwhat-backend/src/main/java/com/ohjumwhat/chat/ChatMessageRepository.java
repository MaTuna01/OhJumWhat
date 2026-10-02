package com.ohjumwhat.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** beforeId보다 오래된 메시지를 최신순으로. 이름은 별명, 없으면 구글 이름 */
	@Query("""
			select new com.ohjumwhat.chat.ChatMessageRow(m.id, u.id, coalesce(u.nickname, u.name), u.profileImageUrl,
				m.body, m.createdAt, m.editedAt, m.deletedAt)
			from ChatMessage m left join com.ohjumwhat.user.User u on u.id = m.userId
			where m.pollId = :pollId and m.id < :beforeId
			order by m.id desc""")
	List<ChatMessageRow> findPage(Long pollId, Long beforeId, Pageable pageable);

	@Query("""
			select new com.ohjumwhat.chat.ChatMessageRow(m.id, u.id, coalesce(u.nickname, u.name), u.profileImageUrl,
				m.body, m.createdAt, m.editedAt, m.deletedAt)
			from ChatMessage m left join com.ohjumwhat.user.User u on u.id = m.userId
			where m.id = :id""")
	Optional<ChatMessageRow> findRow(Long id);
}
