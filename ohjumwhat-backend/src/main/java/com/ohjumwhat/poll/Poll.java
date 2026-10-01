package com.ohjumwhat.poll;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "polls")
public class Poll {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long organizationId;

	/** NULL이면 수동으로 만든 투표 */
	private Long scheduleId;

	/** NULL이면 정기 투표 */
	private Long createdBy;

	@Column(nullable = false)
	private String title;

	/** 한국 날짜. 정기 투표는 열린 날, 수동 투표는 만든 날이다. */
	@Column(nullable = false)
	private LocalDate pollDate;

	@Column(nullable = false)
	private Instant opensAt;

	@Column(nullable = false)
	private Instant closesAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected Poll() {
	}

	private Poll(Long organizationId, Long scheduleId, Long createdBy, String title, LocalDate pollDate,
			Instant opensAt, Instant closesAt) {
		this.organizationId = organizationId;
		this.scheduleId = scheduleId;
		this.createdBy = createdBy;
		this.title = title;
		this.pollDate = pollDate;
		this.opensAt = opensAt;
		this.closesAt = closesAt;
	}

	public static Poll manual(Long organizationId, Long createdBy, String title, LocalDate pollDate, Instant opensAt,
			Instant closesAt) {
		return new Poll(organizationId, null, createdBy, title, pollDate, opensAt, closesAt);
	}

	public static Poll scheduled(Long organizationId, Long scheduleId, String title, LocalDate pollDate,
			Instant opensAt, Instant closesAt) {
		return new Poll(organizationId, scheduleId, null, title, pollDate, opensAt, closesAt);
	}

	/** 마감 처리는 별도 작업 없이, 현재 시각이 마감 시각 이후인지로 판단한다. */
	public boolean isClosed(Instant now) {
		return !now.isBefore(closesAt);
	}

	/** 진행 중인 투표의 제목·마감 시각 수정 */
	public void update(String title, Instant closesAt) {
		this.title = title;
		this.closesAt = closesAt;
	}

	/** 조기 마감: 마감 시각을 지금으로 당긴다. 이후 요청부터 마감된 투표로 판단된다. */
	public void closeAt(Instant now) {
		this.closesAt = now;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public Long getScheduleId() {
		return scheduleId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public String getTitle() {
		return title;
	}

	public LocalDate getPollDate() {
		return pollDate;
	}

	public Instant getOpensAt() {
		return opensAt;
	}

	public Instant getClosesAt() {
		return closesAt;
	}
}
