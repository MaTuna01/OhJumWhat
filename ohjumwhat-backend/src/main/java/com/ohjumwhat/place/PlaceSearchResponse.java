package com.ohjumwhat.place;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 근처 식당 찾기 결과(카카오 로컬, 저장하지 않고 화면에만 보여준다).
 *
 * @param center 검색 기준점(조직 위치)
 * @param places 이름·분류가 맞는 곳(matched)이 앞에 가까운 순, 그다음 메뉴·태그로만 걸린 곳이 가까운 순(45개까지)
 */
@Schema(description = "근처 식당 찾기 결과(저장하지 않고 화면에만 보여준다)")
public record PlaceSearchResponse(
		@Schema(description = "검색 기준점(조직 위치)") PlacesResponse.Center center,
		@Schema(description = "식당(45개까지). matched가 true인 곳이 앞에 가까운 순, 그다음 false인 곳이 가까운 순")
		List<Place> places) {

	/**
	 * @param kakaoPlaceId 카카오 장소 ID(고르면 이 값과 검색어만 저장한다)
	 * @param category 가장 자세한 분류(체인 이름은 뺀다, 예: 떡볶이)
	 * @param distance 조직 위치에서의 직선거리(m). 모르면 null
	 * @param matched 검색어가 이름이나 분류에 들어 있는지(둘러보기는 모두 true). false면 메뉴·태그로만 걸린 곳이다
	 */
	@Schema(name = "PlaceSearchItem", description = "근처 식당 한 곳")
	public record Place(
			@Schema(description = "카카오 장소 ID. 고르면 이 값과 검색어만 메뉴에 저장한다(kakaoPlaceId·placeQuery)",
					example = "12345678") String kakaoPlaceId,
			@Schema(description = "식당 이름", example = "신전떡볶이 역삼점") String name,
			@Schema(description = "가장 자세한 분류(체인 이름은 뺀다)", example = "떡볶이") String category,
			@Schema(description = "도로명 주소", example = "서울 강남구 테헤란로 130") String roadAddress,
			@Schema(description = "위도", example = "37.5012") double lat,
			@Schema(description = "경도", example = "127.0381") double lng,
			@Schema(description = "조직 위치에서의 직선거리(m). 모르면 null", example = "350", nullable = true) Integer distance,
			@Schema(description = "검색어가 이름이나 분류에 들어 있는지(둘러보기는 모두 true). false면 메뉴·태그로만 걸린 곳",
					example = "true") boolean matched) {
	}
}
