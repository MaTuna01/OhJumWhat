package com.ohjumwhat.report;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.sanction.ProfileReset;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.SanctionReason;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "내 사람 신고의 처리 결과(00-R). 처리 순간의 사본이라 나중에 제재가 해제되거나 상대가 탈퇴해도 바뀌지 않는다. "
		+ "관리자 설명은 들어 있지 않다")
public record ReportResult(@Schema(description = "신고 ID(「신고 결과 봤음」에 보낸다)", example = "5") Long id,
		@Schema(description = "신고할 때의 상대 이름", example = "이점심") String targetName,
		@Schema(description = "신고한 시각(UTC)", example = "2026-10-11T02:30:00Z") Instant reportedAt,
		SanctionReason reason,
		ProfileReportResolution resolution,
		@Schema(description = "처리한 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant resolvedAt,
		@Schema(description = "제재의 제한. ACTIONED가 아니면 빈 배열(ACTIONED인데 비어 있으면 경고·초기화만)", example = "[\"CHAT\"]")
		List<Restriction> restrictions,
		@Schema(description = "제재의 프로필 초기화. ACTIONED가 아니면 빈 배열", example = "[]") List<ProfileReset> resets,
		@Schema(description = "제한이 끝나는 시각(UTC). 제한이 없거나 해제할 때까지면 null", example = "2026-10-19T02:30:00Z", nullable = true)
		Instant endsAt) {

	static ReportResult of(ProfileReport report) {
		return new ReportResult(report.getId(), report.getTargetName(), report.getCreatedAt(), report.getReason(),
				report.getResolution(), report.getResolvedAt(), report.getResultRestrictions(),
				report.getResultResets(), report.getResultEndsAt());
	}
}
