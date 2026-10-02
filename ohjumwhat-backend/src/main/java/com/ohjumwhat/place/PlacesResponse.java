package com.ohjumwhat.place;

import java.util.List;

/**
 * 지도에 올릴 위치(볼 때마다 찾는다. 좌표·카카오 결과는 약관상 저장하지 않는다).
 *
 * @param center 조직 위치. 조직 주소가 없거나 찾지 못했으면 null
 * @param places 위치를 찾은 메뉴의 식당. 찾지 못한 메뉴는 빠진다
 */
public record PlacesResponse(Center center, List<Spot> places) {

	/**
	 * @param name 장소 이름(조직 설정의 장소 이름). 없으면 null
	 */
	public record Center(double lat, double lng, String name) {
	}

	/**
	 * 메뉴(optionId)에 붙인 식당의 위치.
	 *
	 * @param name 카카오 식당의 이름(다시 찾은 값). 링크로 붙인 식당은 null(저장한 placeName을 쓴다)
	 * @param category 카카오 식당의 분류. 링크로 붙인 식당은 null
	 * @param roadAddress 카카오 식당의 도로명 주소. 링크로 붙인 식당은 null
	 */
	public record Spot(Long optionId, double lat, double lng, String name, String category, String roadAddress) {
	}
}
