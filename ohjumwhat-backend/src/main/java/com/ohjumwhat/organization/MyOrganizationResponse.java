package com.ohjumwhat.organization;

/**
 * @param hasOpenPollToday 오늘 진행 중인 투표가 있는지
 */
public record MyOrganizationResponse(Long id, String name, long memberCount, boolean hasOpenPollToday) {
}
