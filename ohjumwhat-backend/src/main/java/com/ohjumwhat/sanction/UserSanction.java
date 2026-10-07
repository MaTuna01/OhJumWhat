package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

/**
 * 관리자 제재 한 건(V21). 제한(restrictions)·초기화(resets)가 모두 비면 경고다. 행은 지우지 않고 해제하면 liftedAt만 채운다.
 * 상태는 저장하지 않고 지금 시각으로 계산한다({@link #status}). 해제(관리자)와 안내 확인(본인, update 쿼리)이 같은 순간에
 * 일어나도 서로의 시각을 되쓰지 않게 바뀐 컬럼만 update한다(@DynamicUpdate).
 */
@DynamicUpdate
@Entity
@Table(name = "user_sanctions")
public class UserSanction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	/** 막은 기능(Restriction 이름, 정의 순서). DB 배열이라 문자열로 두고 getter가 enum으로 바꾼다. */
	@Column(nullable = false)
	private String[] restrictions;

	/** 비운 프로필 항목(ProfileReset 이름, 정의 순서) */
	@Column(nullable = false)
	private String[] resets;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SanctionReason reason;

	/** 관리자 설명(본인에게 보인다, 없으면 null) */
	@Column(length = 200)
	private String note;

	@Column(nullable = false)
	private Instant createdAt;

	/** 제한이 끝나는 시각. null이면 해제할 때까지(제한이 있을 때) */
	private Instant endsAt;

	/** 건 관리자. 관리자 회원이 지워지면 null */
	private Long createdBy;

	private Instant liftedAt;

	private Long liftedBy;

	/** 본인이 안내 창을 본 시각. UserSanctionRepository.markSeen으로만 바꾼다. */
	@Column(insertable = false, updatable = false)
	private Instant seenAt;

	protected UserSanction() {
	}

	UserSanction(Long userId, Collection<Restriction> restrictions, Collection<ProfileReset> resets,
			SanctionReason reason, String note, Instant createdAt, Instant endsAt, Long createdBy) {
		this.userId = userId;
		this.restrictions = restrictions.stream().map(Enum::name).toArray(String[]::new);
		this.resets = resets.stream().map(Enum::name).toArray(String[]::new);
		this.reason = reason;
		this.note = note;
		this.createdAt = createdAt;
		this.endsAt = endsAt;
		this.createdBy = createdBy;
	}

	/** 지금 시각 기준 상태(SanctionStatus 설명 참고) */
	public SanctionStatus status(Instant now) {
		if (restrictions.length == 0) {
			return resets.length == 0 ? SanctionStatus.WARNING : SanctionStatus.RESET_ONLY;
		}
		if (liftedAt != null) {
			return SanctionStatus.LIFTED;
		}
		return endsAt != null && !endsAt.isAfter(now) ? SanctionStatus.EXPIRED : SanctionStatus.ACTIVE;
	}

	/** 해제한다(진행 중인 제재만, 확인은 SanctionService가 한다). */
	void lift(Instant now, Long adminId) {
		this.liftedAt = now;
		this.liftedBy = adminId;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public List<Restriction> getRestrictions() {
		return Arrays.stream(restrictions).map(Restriction::valueOf).toList();
	}

	public List<ProfileReset> getResets() {
		return Arrays.stream(resets).map(ProfileReset::valueOf).toList();
	}

	public SanctionReason getReason() {
		return reason;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Instant getLiftedAt() {
		return liftedAt;
	}

	public Long getLiftedBy() {
		return liftedBy;
	}

	public Instant getSeenAt() {
		return seenAt;
	}
}
