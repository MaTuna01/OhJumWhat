package com.ohjumwhat.report;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.sanction.ProfileReset;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.SanctionReason;

/**
 * 신고한 사람에게 보여줄 처리 결과 한 건(GET /api/sanctions/alerts의 reportResults). 처리 순간의 사본이라 나중에 제재가
 * 해제되거나 신고된 사람이 탈퇴해도 바뀌지 않는다. 관리자 설명(note)은 넣지 않는다.
 *
 * @param targetName 신고할 때의 이름
 * @param reportedAt 신고한 시각
 * @param restrictions 제재의 제한(ACTIONED가 아니면 빈 목록)
 * @param resets 제재의 프로필 초기화(ACTIONED가 아니면 빈 목록)
 * @param endsAt 제한이 끝나는 시각(제한이 없거나 해제할 때까지면 null)
 */
public record ReportResult(Long id, String targetName, Instant reportedAt, SanctionReason reason,
		ProfileReportResolution resolution, Instant resolvedAt, List<Restriction> restrictions,
		List<ProfileReset> resets, Instant endsAt) {

	static ReportResult of(ProfileReport report) {
		return new ReportResult(report.getId(), report.getTargetName(), report.getCreatedAt(), report.getReason(),
				report.getResolution(), report.getResolvedAt(), report.getResultRestrictions(),
				report.getResultResets(), report.getResultEndsAt());
	}
}
