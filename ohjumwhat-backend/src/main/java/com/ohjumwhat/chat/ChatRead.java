package com.ohjumwhat.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 투표 채팅의 읽은 위치: 한 사람이 그 투표에서 「이 메시지까지 봤다」. (투표, 사람)당 한 행이고, 행이 없으면 0이다.
 * 쓰기는 {@link ChatReadRepository#markRead}(네이티브 upsert)로만 한다. 위치는 뒤로 가지 않는다.
 */
@Entity
@Table(name = "chat_reads")
public class ChatRead {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pollId;

	@Column(nullable = false)
	private Long userId;

	/** 마지막으로 본 메시지 ID(chat_messages.id) */
	@Column(nullable = false)
	private Long lastReadMessageId;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ChatRead() {
	}

	public Long getId() {
		return id;
	}

	public Long getPollId() {
		return pollId;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getLastReadMessageId() {
		return lastReadMessageId;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
