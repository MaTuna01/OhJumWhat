package com.ohjumwhat.user;

/**
 * @param name 화면에 보여줄 이름(별명, 없으면 구글 이름)
 * @param nickname 마이페이지에서 정한 별명. 없으면 null
 * @param googleName 구글 계정 이름
 * @param lastVisitedOrgId 로그인 후 이동할 최근 조직. 속한 조직이 없으면 null
 * @param admin 관리자면 프로필 메뉴에 관리자 콘솔이 보인다
 */
public record MeResponse(Long id, String name, String nickname, String googleName, String email,
		String profileImageUrl, Long lastVisitedOrgId, boolean admin) {
}
