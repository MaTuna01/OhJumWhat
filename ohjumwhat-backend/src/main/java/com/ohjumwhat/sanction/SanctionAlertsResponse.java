package com.ohjumwhat.sanction;

import java.util.List;

import com.ohjumwhat.report.ReportResult;

/**
 * 아직 보지 않은 안내.
 *
 * @param sanctions 내 제재(오래된 순, 최대 20건)
 * @param reportResults 내 사람 신고의 처리 결과(처리 시각 오래된 순, 최대 20건)
 */
public record SanctionAlertsResponse(List<SanctionNotice> sanctions, List<ReportResult> reportResults) {
}
