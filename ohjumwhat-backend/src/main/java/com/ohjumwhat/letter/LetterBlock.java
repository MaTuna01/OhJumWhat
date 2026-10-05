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
 * 익명 차단은 그 사람의 익명 쪽지만, 실명 차단은 실명 쪽지만 숨긴다(차단 때문에 익명이 드러나지 않게).
 */
@Entity
@Table(name = "letter_blocks")
public class LetterBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
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
