package com.ohjumwhat.menu;

/**
 * 같은 조직에서 같은 이름의 메뉴에 지난번 붙인 식당(자동완성·추천에서 「지난번: 할매집」). 고르면 이 식당을 붙여 메뉴를 추가한다.
 * 카카오 식당은 이름을 저장하지 않으므로, 화면은 optionId로 /api/orgs/{id}/places에서 이름을 받는다.
 *
 * @param optionId 그 식당을 붙였던 메뉴
 * @param placeName 링크로 붙인 식당의 이름(없으면 null)
 * @param kakaoPlaceId 근처 식당 찾기로 고른 카카오 장소 ID(없으면 null)
 * @param placeQuery 카카오 식당을 찾을 때 친 검색어(없으면 null)
 */
public record LastPlace(Long optionId, String link, String placeName, String placeAddress, String kakaoPlaceId,
		String placeQuery) {

	static LastPlace of(MenuOption option) {
		return new LastPlace(option.getId(), option.getLinkUrl(), option.getPlaceName(), option.getPlaceAddress(),
				option.getKakaoPlaceId(), option.getPlaceQuery());
	}
}
