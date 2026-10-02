package com.ohjumwhat.place;

import java.util.List;

/**
 * 근처 식당 찾기 결과(카카오 로컬, 저장하지 않고 화면에만 보여준다).
 *
 * @param center 검색 기준점(조직 위치)
 * @param places 이름·분류가 맞는 곳(matched)이 앞에 가까운 순, 그다음 메뉴·태그로만 걸린 곳이 가까운 순(45개까지)
 */
public record PlaceSearchResponse(PlacesResponse.Center center, List<Place> places) {

	/**
	 * @param kakaoPlaceId 카카오 장소 ID(고르면 이 값과 검색어만 저장한다)
	 * @param category 가장 자세한 분류(체인 이름은 뺀다, 예: 떡볶이)
	 * @param distance 조직 위치에서의 직선거리(m). 모르면 null
	 * @param matched 검색어가 이름이나 분류에 들어 있는지(둘러보기는 모두 true). false면 메뉴·태그로만 걸린 곳이다
	 */
	public record Place(String kakaoPlaceId, String name, String category, String roadAddress, double lat, double lng,
			Integer distance, boolean matched) {
	}
}
