package com.ohjumwhat.report;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.sanction.ProfileReset;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.SanctionReason;
import com.ohjumwhat.user.Role;
import com.ohjumwhat.user.User;

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
public record ProfileReportRow(Long id, Instant reportedAt, SanctionReason reason, String detail,
		ProfileReportResolution resolution, Instant resolvedAt, String resolvedByName, Long targetId,
		String targetName, String targetEmail, String targetProfileImageUrl, boolean targetAdmin,
		Profile snapshot, Profile current, Long reporterId, String reporterName, String reporterEmail,
		Result result) {

	/** 비교용 프로필(사진 제외). 비어 있는 항목은 null·빈 목록 */
	public record Profile(String name, String bio, List<String> foodTags, List<String> hobbies, String jobTitle) {
	}

	/** 신고한 사람에게 보여준 제재 결과 사본 */
	public record Result(List<Restriction> restrictions, List<ProfileReset> resets, Instant endsAt) {
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
