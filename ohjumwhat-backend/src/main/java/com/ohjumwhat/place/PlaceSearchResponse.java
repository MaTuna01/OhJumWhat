package com.ohjumwhat.place;

import java.util.List;

/**
 * 근처 식당 찾기 결과(카카오 로컬, 저장하지 않고 화면에만 보여준다).
 *
 * @param center 검색 기준점(회사 위치)
 * @param places 가까운 순 음식점
 * @param hasMore 다음 페이지가 있는지(45개까지)
 */
public record PlaceSearchResponse(PlacesResponse.Center center, List<Place> places, boolean hasMore) {

	/**
	 * @param kakaoPlaceId 카카오 장소 ID(고르면 이 값과 검색어만 저장한다)
	 * @param category 가장 자세한 분류(예: 찌개,전골)
	 * @param distance 회사에서의 직선거리(m). 모르면 null
	 */
	public record Place(String kakaoPlaceId, String name, String category, String roadAddress, double lat, double lng,
			Integer distance) {
	}
}
