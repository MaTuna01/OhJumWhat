package com.ohjumwhat.letter;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 받은 사람이 신고한 쪽지. 관리자가 처리 완료하면 resolvedAt·resolvedBy가 생긴다. */
@Entity
@Table(name = "letter_reports")
public class LetterReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private Long letterId;

	@Column(length = 100)
	private String reason;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant resolvedAt;

	private Long resolvedBy;

	protected LetterReport() {
	}

	LetterReport(Long letterId, String reason, Instant createdAt) {
		this.letterId = letterId;
		this.reason = reason;
		this.createdAt = createdAt;
	}

	/** 처음 처리한 관리자와 시각만 남긴다. */
	public void resolve(Long adminId, Instant now) {
		if (resolvedAt == null) {
			resolvedAt = now;
			resolvedBy = adminId;
		}
	}

	public Long getId() {
		return id;
	}
}
