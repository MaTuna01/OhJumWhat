package com.ohjumwhat.report;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.sanction.ProfileReset;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.SanctionReason;
import com.ohjumwhat.user.Role;
import com.ohjumwhat.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 콘솔의 사람 신고 한 줄. 사람 이름은 별명(없으면 구글 이름), 사진은 올린 사진(없으면 구글 사진)이다.
 * 신고된 사람·신고한 사람이 강제 탈퇴했으면 그 사람의 id·이름·이메일(신고된 사람은 사진·current도)이 null이다.
 *
 * @param resolution 처리 결과(처리 전이면 null)
 * @param resolvedByName 처리한 관리자(처리 전이거나 관리자 회원이 지워졌으면 null)
 * @param targetAdmin 신고된 사람이 지금 관리자다(관리자는 제재할 수 없어 「문제 없음」으로만 처리한다)
 * @param snapshot 신고할 때의 프로필
 * @param current 지금 프로필(신고된 사람이 탈퇴했으면 null)
 * @param result 제재 결과 사본(ACTIONED일 때만, 아니면 null)
 */
@Schema(description = "관리자 콘솔의 사람 신고 한 줄(A09c). 신고된 사람·신고한 사람이 강제 탈퇴했으면 그 사람의 id·이름·이메일"
		+ "(신고된 사람은 사진·current도)이 null이다")
public record ProfileReportRow(@Schema(description = "신고 ID", example = "5") Long id,
		@Schema(description = "신고한 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant reportedAt,
		SanctionReason reason,
		@Schema(description = "신고 설명(선택, 100자)", example = "프로필 사진이 부적절해요", nullable = true) String detail,
		@Schema(description = "처리 결과. 처리 전이면 null", nullable = true) ProfileReportResolution resolution,
		@Schema(description = "처리한 시각(UTC). 처리 전이면 null", nullable = true) Instant resolvedAt,
		@Schema(description = "처리한 관리자 이름(처리 전이거나 관리자 회원이 지워졌으면 null)", example = "관리자", nullable = true)
		String resolvedByName,
		@Schema(description = "신고된 사람 ID. 탈퇴했으면 null", example = "8", nullable = true) Long targetId,
		@Schema(description = "신고된 사람의 지금 이름(별명, 없으면 구글 이름)", example = "이점심", nullable = true) String targetName,
		@Schema(description = "신고된 사람 이메일", example = "lee@example.com", nullable = true) String targetEmail,
		@Schema(description = "신고된 사람의 지금 사진(올린 사진, 없으면 구글 사진)", example = "/api/photos/3f2a9c1e.jpg", nullable = true)
		String targetProfileImageUrl,
		@Schema(description = "신고된 사람이 지금 관리자인지(관리자는 제재할 수 없어 「제재하기」를 숨기고 「문제 없음」으로만 처리한다)",
				example = "false")
		boolean targetAdmin,
		@Schema(description = "신고할 때의 프로필") Profile snapshot,
		@Schema(description = "지금 프로필. 신고된 사람이 탈퇴했으면 null", nullable = true) Profile current,
		@Schema(description = "신고한 사람 ID. 탈퇴했으면 null", example = "7", nullable = true) Long reporterId,
		@Schema(description = "신고한 사람 이름", example = "김오점", nullable = true) String reporterName,
		@Schema(description = "신고한 사람 이메일", example = "kim@example.com", nullable = true) String reporterEmail,
		@Schema(description = "제재 결과 사본(ACTIONED일 때만, 아니면 null)", nullable = true) Result result) {

	/** 비교용 프로필(사진 제외). 비어 있는 항목은 null·빈 목록 */
	@Schema(name = "ProfileReportProfile", description = "비교용 프로필(사진 제외). 비어 있는 항목은 null·빈 배열")
	public record Profile(@Schema(description = "이름(별명, 없으면 구글 이름)", example = "이점심", nullable = true) String name,
			@Schema(description = "한줄 소개", example = "점심은 무조건 국밥", nullable = true) String bio,
			@Schema(description = "좋아하는 음식", example = "[\"국밥\"]") List<String> foodTags,
			@Schema(description = "취미", example = "[\"등산\"]") List<String> hobbies,
			@Schema(description = "직급", example = "대리", nullable = true) String jobTitle) {
	}

	/** 신고한 사람에게 보여준 제재 결과 사본 */
	@Schema(name = "ProfileReportResult", description = "신고한 사람에게 보여준 제재 결과 사본")
	public record Result(@Schema(description = "제한(경고·초기화만이면 빈 배열)", example = "[\"CHAT\"]") List<Restriction> restrictions,
			@Schema(description = "프로필 초기화(없으면 빈 배열)", example = "[]") List<ProfileReset> resets,
			@Schema(description = "제한이 끝나는 시각(UTC). 제한이 없거나 해제할 때까지면 null", nullable = true) Instant endsAt) {
	}

	/**
	 * JPQL로 고른 재료(ProfileReportRepository). 신고된 사람·신고한 사람·처리한 관리자는 left join이라 지워졌으면 null이다.
	 *
	 * @param photoKey 신고된 사람이 올린 사진의 키(없으면 null)
	 * @param googleUrl 신고된 사람의 구글 사진 주소(없으면 null)
	 */
	public record Source(ProfileReport report, Long targetId, String targetName, String targetEmail, String photoKey,
			String googleUrl, Role targetRole, String targetBio, String[] targetFoodTags, String[] targetHobbies,
			String targetJobTitle, Long reporterId, String reporterName, String reporterEmail, String resolvedByName) {

		ProfileReportRow toRow() {
			Profile snapshot = new Profile(report.getTargetName(), report.getTargetBio(), report.getTargetFoodTags(),
					report.getTargetHobbies(), report.getTargetJobTitle());
			Profile current = targetId == null ? null
					: new Profile(targetName, targetBio, User.tagList(targetFoodTags), User.tagList(targetHobbies),
							targetJobTitle);
			Result result = report.getResolution() == ProfileReportResolution.ACTIONED
					? new Result(report.getResultRestrictions(), report.getResultResets(), report.getResultEndsAt())
					: null;
			return new ProfileReportRow(report.getId(), report.getCreatedAt(), report.getReason(), report.getDetail(),
					report.getResolution(), report.getResolvedAt(), resolvedByName, targetId, targetName, targetEmail,
					targetId == null ? null : User.photoUrl(photoKey, googleUrl), targetRole == Role.ADMIN, snapshot,
					current, reporterId, reporterName, reporterEmail, result);
		}
	}
}
