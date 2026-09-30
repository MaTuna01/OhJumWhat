package com.ohjumwhat.user;

/**
 * @param lastVisitedOrgId 로그인 후 이동할 최근 조직. 속한 조직이 없으면 null
 */
public record MeResponse(Long id, String name, String email, String profileImageUrl, Long lastVisitedOrgId) {
}
