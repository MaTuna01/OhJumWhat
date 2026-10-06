package com.ohjumwhat.guestbook;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 주인이 신고한 방명록 글(신고하는 사람은 언제나 주인이다). 관리자가 처리하면 resolution·resolvedAt·resolvedBy가 생긴다. */
@Entity
@Table(name = "guestbook_reports")
public class GuestbookReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private Long entryId;

	@Column(length = 100)
	private String reason;

	@Column(nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private GuestbookReportResolution resolution;

	private Instant resolvedAt;

	private Long resolvedBy;

	protected GuestbookReport() {
	}

	GuestbookReport(Long entryId, String reason, Instant createdAt) {
		this.entryId = entryId;
		this.reason = reason;
		this.createdAt = createdAt;
	}

	/** 처음 처리한 결과·관리자·시각만 남긴다(이미 처리한 신고를 다른 결과로 바꾸는 것은 GuestbookService가 409로 막는다). */
	void resolve(GuestbookReportResolution resolution, Long adminId, Instant now) {
		if (this.resolution == null) {
			this.resolution = resolution;
			this.resolvedBy = adminId;
			this.resolvedAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getEntryId() {
		return entryId;
	}

	public GuestbookReportResolution getResolution() {
		return resolution;
	}
}
