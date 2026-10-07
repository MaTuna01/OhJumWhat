package com.ohjumwhat.report;

/** 관리자가 사람 신고를 처리했다(신고한 사람에게 알릴 거리). 설명·결과는 싣지 않는다. 신고한 사람이 탈퇴했으면 reporterId는 null */
public record ProfileReportResolvedEvent(Long reportId, Long reporterId) {
}
