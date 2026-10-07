package com.ohjumwhat.report;

import java.time.Instant;
import java.util.Arrays;
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

import com.ohjumwhat.sanction.ProfileReset;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.SanctionReason;
import com.ohjumwhat.sanction.UserSanction;
import com.ohjumwhat.user.User;

/**
 * 사람 신고 한 건(V22). 신고할 때의 프로필 사본을 함께 둔다. 행은 신고할 때 ProfileReportRepository.insertOpen으로만 만들고
 * (사본을 회원 행에서 바로 고르고, 처리 전 신고가 이미 있으면 아무것도 하지 않는다), 관리자가 처리하면 결과·시각·관리자와
 * (제재했으면) 결과 사본이 생긴다. 신고한 사람·신고된 사람이 강제 탈퇴하면 FK가 ID를 비우므로, 처리(관리자)가 그 ID나
 * 결과 확인 시각(신고한 사람, update 쿼리)을 되쓰지 않게 바뀐 컬럼만 update한다(@DynamicUpdate).
 */
@DynamicUpdate
@Entity
@Table(name = "profile_reports")
public class ProfileReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 신고한 사람. 강제 탈퇴로 지워지면 null */
	private Long reporterId;

	/** 신고된 사람. 강제 탈퇴로 지워지면 null */
	private Long targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SanctionReason reason;

	/** 설명(선택, 한 줄 100자) */
	@Column(length = 100)
	private String detail;

	/** 신고할 때의 이름(별명, 없으면 구글 이름) */
	@Column(nullable = false, length = 100)
	private String targetName;

	@Column(length = 50)
	private String targetBio;

	@Column(nullable = false)
	private String[] targetFoodTags;

	@Column(nullable = false)
	private String[] targetHobbies;

	@Column(length = 15)
	private String targetJobTitle;

	@Column(nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ProfileReportResolution resolution;

	private Instant resolvedAt;

	/** 처리한 관리자. 관리자 회원이 지워지면 null */
	private Long resolvedBy;

	/** 함께 처리한 제재(ACTIONED). 제재가 회원과 함께 지워지면 null */
	private Long sanctionId;

	/** 결과 사본: 제재에 실제로 저장된 제한(Restriction 이름, 정의 순서). ACTIONED가 아니면 비어 있다. */
	@Column(nullable = false)
	private String[] resultRestrictions;

	/** 결과 사본: 제재에 실제로 저장된 초기화(ProfileReset 이름, 정의 순서) */
	@Column(nullable = false)
	private String[] resultResets;

	/** 결과 사본: 제한이 끝나는 시각(제한이 없거나 해제할 때까지면 null) */
	private Instant resultEndsAt;

	/** 신고한 사람이 결과 창을 본 시각. ProfileReportRepository.markResultsSeen으로만 바꾼다. */
	@Column(insertable = false, updatable = false)
	private Instant resultSeenAt;

	protected ProfileReport() {
	}

	/** 문제 없음·강제 탈퇴로 처리한다(처리 전인지는 ProfileReportService가 확인한다). */
	void resolve(ProfileReportResolution resolution, Long adminId, Instant now) {
		this.resolution = resolution;
		this.resolvedBy = adminId;
		this.resolvedAt = now;
	}

	/** 제재로 처리하고, 신고한 사람에게 보여줄 결과를 제재에 실제로 저장된 값으로 베껴 둔다(나중에 해제·탈퇴돼도 그대로). */
	void action(UserSanction sanction, Long adminId, Instant now) {
		resolve(ProfileReportResolution.ACTIONED, adminId, now);
		this.sanctionId = sanction.getId();
		this.resultRestrictions = sanction.getRestrictions().stream().map(Enum::name).toArray(String[]::new);
		this.resultResets = sanction.getResets().stream().map(Enum::name).toArray(String[]::new);
		this.resultEndsAt = sanction.getEndsAt();
	}

	public Long getId() {
		return id;
	}

	public Long getReporterId() {
		return reporterId;
	}

	public Long getTargetId() {
		return targetId;
	}

	public SanctionReason getReason() {
		return reason;
	}

	public String getDetail() {
		return detail;
	}

	public String getTargetName() {
		return targetName;
	}

	public String getTargetBio() {
		return targetBio;
	}

	public List<String> getTargetFoodTags() {
		return User.tagList(targetFoodTags);
	}

	public List<String> getTargetHobbies() {
		return User.tagList(targetHobbies);
	}

	public String getTargetJobTitle() {
		return targetJobTitle;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public ProfileReportResolution getResolution() {
		return resolution;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Long getResolvedBy() {
		return resolvedBy;
	}

	public Long getSanctionId() {
		return sanctionId;
	}

	public List<Restriction> getResultRestrictions() {
		return resultRestrictions == null ? List.of()
				: Arrays.stream(resultRestrictions).map(Restriction::valueOf).toList();
	}

	public List<ProfileReset> getResultResets() {
		return resultResets == null ? List.of() : Arrays.stream(resultResets).map(ProfileReset::valueOf).toList();
	}

	public Instant getResultEndsAt() {
		return resultEndsAt;
	}

	public Instant getResultSeenAt() {
		return resultSeenAt;
	}
}
