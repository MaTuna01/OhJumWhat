package com.ohjumwhat.sanction;

import java.util.List;

import com.ohjumwhat.report.ReportResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 아직 보지 않은 안내.
 *
 * @param sanctions 내 제재(오래된 순, 최대 20건)
 * @param reportResults 내 사람 신고의 처리 결과(처리 시각 오래된 순, 최대 20건)
 */
@Schema(description = "아직 보지 않은 안내. 화면은 제재 안내 → 방명록 글 제한 경고 → 신고 결과 순서로 하나씩 띄운다")
public record SanctionAlertsResponse(
		@Schema(description = "아직 안내 창에서 보지 않은 내 제재(오래된 순, 최대 20건). 제재 안내 창(00-S)에 1 / N으로 보여준다")
		List<SanctionNotice> sanctions,
		@Schema(description = "아직 보지 않은 내 사람 신고의 처리 결과(처리 시각 오래된 순, 최대 20건). 신고 결과 창(00-R)")
		List<ReportResult> reportResults) {
}
