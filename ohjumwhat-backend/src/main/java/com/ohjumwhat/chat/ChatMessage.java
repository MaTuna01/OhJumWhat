package com.ohjumwhat.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 투표 채팅 메시지. 지우면 행은 남기고 본문만 비운다("삭제된 메시지예요"). */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pollId;

	// 쓴 사람이 강제 탈퇴로 삭제되면 NULL이 된다(메시지는 남는다).
	private Long userId;

	/** 지운 메시지는 NULL */
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant editedAt;

	private Instant deletedAt;

	protected ChatMessage() {
	}

	public ChatMessage(Long pollId, Long userId, String body, Instant createdAt) {
		this.pollId = pollId;
		this.userId = userId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public void edit(String body, Instant now) {
		this.body = body;
		this.editedAt = now;
	}

	public void delete(Instant now) {
		this.body = null;
		this.deletedAt = now;
	}

	public boolean isDeleted() {
		return deletedAt != null;
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
}
