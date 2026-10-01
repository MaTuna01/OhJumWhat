package com.ohjumwhat.user;

/**
 * @param lastVisitedOrgId 로그인 후 이동할 최근 조직. 속한 조직이 없으면 null
 * @param admin 관리자면 프로필 메뉴에 관리자 콘솔이 보인다
 */
public record MeResponse(Long id, String name, String email, String profileImageUrl, Long lastVisitedOrgId,
		boolean admin) {
}
