package com.ohjumwhat.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** beforeId보다 오래된 메시지를 최신순으로. 이름은 별명(없으면 구글 이름), 사진은 올린 사진(없으면 구글 사진) */
	@Query("""
			select new com.ohjumwhat.chat.ChatMessageRow(m.id, u.id, coalesce(u.nickname, u.name), u.photoKey,
				u.profileImageUrl, m.body, m.imageKey, m.imageWidth, m.imageHeight, m.createdAt, m.editedAt, m.deletedAt)
			from ChatMessage m left join com.ohjumwhat.user.User u on u.id = m.userId
			where m.pollId = :pollId and m.id < :beforeId
			order by m.id desc""")
	List<ChatMessageRow> findPage(Long pollId, Long beforeId, Pageable pageable);

	@Query("""
			select new com.ohjumwhat.chat.ChatMessageRow(m.id, u.id, coalesce(u.nickname, u.name), u.photoKey,
				u.profileImageUrl, m.body, m.imageKey, m.imageWidth, m.imageHeight, m.createdAt, m.editedAt, m.deletedAt)
			from ChatMessage m left join com.ohjumwhat.user.User u on u.id = m.userId
			where m.id = :id""")
	Optional<ChatMessageRow> findRow(Long id);

	/**
	 * 이 사람이 이 사진을 볼 수 있는지: 지우지 않았고 보관 기간(createdAt > cutoff) 안의 사진 메시지이고,
	 * 그 투표 조직의 멤버이거나 관리자(관리자 콘솔은 멤버가 아니어도 채팅을 본다)일 때.
	 */
	@Query("""
			select count(m) > 0 from ChatMessage m join com.ohjumwhat.poll.Poll p on p.id = m.pollId
			where m.imageKey = :key and m.deletedAt is null and m.createdAt > :cutoff
				and (exists (select 1 from com.ohjumwhat.organization.Membership ms
						where ms.organizationId = p.organizationId and ms.userId = :userId)
					or exists (select 1 from com.ohjumwhat.user.User u
						where u.id = :userId and u.role = com.ohjumwhat.user.Role.ADMIN))""")
	boolean canViewPhoto(String key, Long userId, Instant cutoff);

	/** 이 키들 중 아직 보여주는 사진(지우지 않았고 보관 기간 안)의 키. 정리 작업이 나머지 파일을 지운다. */
	@Query("""
			select m.imageKey from ChatMessage m
			where m.imageKey in :keys and m.deletedAt is null and m.createdAt > :cutoff""")
	List<String> findLiveImageKeys(Collection<String> keys, Instant cutoff);
}
