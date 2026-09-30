package com.ohjumwhat.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 정기 투표 규칙. 요일·시간은 한국 시간 기준이다. */
@Entity
@Table(name = "poll_schedules")
public class PollSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long organizationId;

	@Column(nullable = false)
	private String name;

	/** 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64를 더한 값 (평일 = 31) */
	@Column(nullable = false)
	private short daysOfWeek;

	@Column(nullable = false)
	private LocalTime openTime;

	@Column(nullable = false)
	private LocalTime closeTime;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected PollSchedule() {
	}

	public PollSchedule(Long organizationId, String name, int daysOfWeek, LocalTime openTime, LocalTime closeTime) {
		this.organizationId = organizationId;
		update(name, daysOfWeek, openTime, closeTime);
	}

	public void update(String name, int daysOfWeek, LocalTime openTime, LocalTime closeTime) {
		this.name = name;
		this.daysOfWeek = (short) daysOfWeek;
		this.openTime = openTime;
		this.closeTime = closeTime;
	}

	public boolean runsOn(DayOfWeek day) {
		return (daysOfWeek & bitOf(day)) != 0;
	}

	public static int bitOf(DayOfWeek day) {
		return 1 << (day.getValue() - 1);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public String getName() {
		return name;
	}

	public int getDaysOfWeek() {
		return daysOfWeek;
	}

	public LocalTime getOpenTime() {
		return openTime;
	}

	public LocalTime getCloseTime() {
		return closeTime;
	}
}
