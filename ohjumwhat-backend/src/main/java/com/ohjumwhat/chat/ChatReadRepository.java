package com.ohjumwhat.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatReadRepository extends JpaRepository<ChatRead, Long> {

	/** 내가 그 투표에서 마지막으로 본 메시지 ID(본 적이 없으면 비어 있다) */
	@Query("select r.lastReadMessageId from ChatRead r where r.pollId = :pollId and r.userId = :userId")
	Optional<Long> findLastReadId(Long pollId, Long userId);

	/**
	 * 「messageId까지 봤다」. (poll_id, user_id) 행이 있으면 더 뒤일 때만 옮기고(뒤로 가지 않는다), 동시에 보내도 행이 하나로
	 * 남도록 DB의 ON CONFLICT로 처리한다. 위치는 그 투표의 마지막 메시지를 넘지 않고, 메시지가 없는 투표에는 행을 만들지 않는다.
	 *
	 * @return 바뀐 행 수(메시지가 없으면 0)
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			insert into chat_reads (poll_id, user_id, last_read_message_id, updated_at)
			select :pollId, :userId, least(:messageId, max(m.id)), now()
			from chat_messages m
			where m.poll_id = :pollId
			having max(m.id) is not null
			on conflict (poll_id, user_id)
			do update set last_read_message_id = greatest(chat_reads.last_read_message_id, excluded.last_read_message_id),
				updated_at = excluded.updated_at""", nativeQuery = true)
	int markRead(Long pollId, Long userId, long messageId);

	/**
	 * 투표별 안 읽은 메시지 수: 남이 쓴(쓴 사람이 탈퇴해 NULL이면 남의 글), 지우지 않은, 읽은 위치(없으면 0)보다 뒤의 메시지.
	 * 안 읽은 메시지가 없는 투표는 빠진다.
	 */
	@Query("""
			select new com.ohjumwhat.chat.ChatUnreadCount(m.pollId, count(m))
			from ChatMessage m left join ChatRead r on r.pollId = m.pollId and r.userId = :userId
			where m.pollId in :pollIds
			  and m.deletedAt is null
			  and (m.userId is null or m.userId <> :userId)
			  and m.id > coalesce(r.lastReadMessageId, 0L)
			group by m.pollId""")
	List<ChatUnreadCount> countUnread(Collection<Long> pollIds, Long userId);
}
