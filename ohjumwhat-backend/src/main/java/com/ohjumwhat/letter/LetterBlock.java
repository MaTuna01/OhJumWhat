package com.ohjumwhat.letter;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 받은 사람이 보낸 사람을 차단한 것. 익명 쪽지에서 한 차단(anonymous)과 실명 쪽지에서 한 차단을 나눈다.
 * 실명 차단은 사람 단위로 그 사람의 실명 쪽지를 숨기고, 익명 차단은 쪽지 한 통(letterId) 단위로 그 쪽지만 숨긴다
 * (차단 때문에 익명이 드러나지 않게). 새로 오는 익명 쪽지는 그 사람에게 걸린 차단이 하나라도 있으면 받지 않는다.
 */
@Entity
@Table(name = "letter_blocks")
public class LetterBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	/** 보낸 사람이 강제 탈퇴하면 null(차단은 남는다) */
	private Long blockedUserId;

	private Long letterId;

	@Column(nullable = false)
	private boolean anonymous;

	@Column(nullable = false)
	private Instant createdAt;

	protected LetterBlock() {
	}

	LetterBlock(Long userId, Long blockedUserId, Long letterId, boolean anonymous, Instant createdAt) {
		this.userId = userId;
		this.blockedUserId = blockedUserId;
		this.letterId = letterId;
		this.anonymous = anonymous;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}
}
