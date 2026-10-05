package com.ohjumwhat.letter;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 쪽지 한 통. 사용자는 행을 지우지 않고 자기 쪽에서만 지운다(senderDeletedAt·recipientDeletedAt).
 * 익명이면 받은 사람의 응답에 보낸 사람을 내보내지 않는다(LetterRepository가 이름·사진을 고르지 않는다).
 */
@Entity
@Table(name = "letters")
public class Letter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 조직이 삭제되면 NULL
	private Long organizationId;

	// 강제 탈퇴로 회원이 지워지면 NULL
	private Long senderId;

	@Column(nullable = false)
	private Long recipientId;

	private Long replyToId;

	@Column(nullable = false)
	private boolean anonymous;

	/** 익명 쪽지에 쓴 답장: 보낸 사람에게 받는 사람을 숨긴다 */
	@Column(nullable = false)
	private boolean recipientHidden;

	@Column(nullable = false, length = 500)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant readAt;

	private Instant senderDeletedAt;

	/** 받은 사람이 지웠거나, 차단해 두어 받지 않은 쪽지 */
	private Instant recipientDeletedAt;

	protected Letter() {
	}

	Letter(Long organizationId, Long senderId, Long recipientId, Long replyToId, boolean anonymous,
			boolean recipientHidden, String body, Instant createdAt) {
		this.organizationId = organizationId;
		this.senderId = senderId;
		this.recipientId = recipientId;
		this.replyToId = replyToId;
		this.anonymous = anonymous;
		this.recipientHidden = recipientHidden;
		this.body = body;
		this.createdAt = createdAt;
	}

	/** 처음 읽은 시각만 남긴다. */
	void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	void deleteForSender(Instant now) {
		senderDeletedAt = now;
	}

	void deleteForRecipient(Instant now) {
		recipientDeletedAt = now;
	}

	/** 받은 사람이 차단해 두었다: 보낸 사람에게는 보낸 것으로 보이고, 받은 사람에게는 가지 않는다. */
	void dropForRecipient(Instant now) {
		recipientDeletedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public Long getSenderId() {
		return senderId;
	}

	public Long getRecipientId() {
		return recipientId;
	}

	public boolean isAnonymous() {
		return anonymous;
	}

	public boolean isRecipientHidden() {
		return recipientHidden;
	}

	public Instant getSenderDeletedAt() {
		return senderDeletedAt;
	}
}
