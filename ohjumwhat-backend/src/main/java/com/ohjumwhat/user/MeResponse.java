package com.ohjumwhat.user;

import java.util.List;

/**
 * @param name 화면에 보여줄 이름(별명, 없으면 구글 이름)
 * @param nickname 마이페이지에서 정한 별명. 없으면 null
 * @param googleName 구글 계정 이름
 * @param profileImageUrl 화면에 보여줄 사진(올린 사진, 없으면 구글 사진). 둘 다 없으면 null
 * @param googleProfileImageUrl 구글 프로필 사진. 「구글 사진으로 되돌리기」 미리보기에 쓴다
 * @param customPhoto 직접 올린 사진이 있으면 true
 * @param bio 한줄 소개. 없으면 null
 * @param foodTags 좋아하는 음식(적은 순서, 최대 3개). 없으면 빈 목록
 * @param lastVisitedOrgId 로그인 후 이동할 최근 조직. 속한 조직이 없으면 null
 * @param admin 관리자면 프로필 메뉴에 관리자 콘솔이 보인다
 */
public record MeResponse(Long id, String name, String nickname, String googleName, String email,
		String profileImageUrl, String googleProfileImageUrl, boolean customPhoto, String bio, List<String> foodTags,
		Long lastVisitedOrgId, boolean admin) {
}
