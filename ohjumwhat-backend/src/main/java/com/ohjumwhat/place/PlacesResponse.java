package com.ohjumwhat.place;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지도에 올릴 위치(볼 때마다 찾는다. 좌표·카카오 결과는 약관상 저장하지 않는다).
 *
 * @param center 조직 위치. 조직 주소가 없거나 찾지 못했으면 null
 * @param places 위치를 찾은 메뉴의 식당. 찾지 못한 메뉴는 빠진다
 */
@Schema(description = "지도에 올릴 위치(볼 때마다 찾는다, 저장·캐시하지 않는다)")
public record PlacesResponse(
		@Schema(description = "조직 위치. 조직 주소가 없거나 찾지 못했거나 지도가 꺼져 있으면 null", nullable = true) Center center,
		@Schema(description = "위치를 찾은 메뉴의 식당. 찾지 못한 메뉴는 빠진다") List<Spot> places) {

	/**
	 * @param name 장소 이름(조직 설정의 장소 이름). 없으면 null
	 */
	@Schema(name = "MapCenter", description = "조직 위치(지도의 기준점, 거리·도보 시간 계산의 출발점)")
	public record Center(
			@Schema(description = "위도", example = "37.5009") double lat,
			@Schema(description = "경도", example = "127.0364") double lng,
			@Schema(description = "장소 이름(조직 설정의 장소 이름). 없으면 null", example = "본사 3층", nullable = true) String name) {
	}

	/**
	 * 메뉴(optionId)에 붙인 식당의 위치.
	 *
	 * @param name 카카오 식당의 이름(다시 찾은 값). 링크로 붙인 식당은 null(저장한 placeName을 쓴다)
	 * @param category 카카오 식당의 분류. 링크로 붙인 식당은 null
	 * @param roadAddress 카카오 식당의 도로명 주소. 링크로 붙인 식당은 null
	 */
	@Schema(name = "PlaceSpot", description = "메뉴에 붙인 식당의 위치(지도 핀)")
	public record Spot(
			@Schema(description = "메뉴 ID", example = "100") Long optionId,
			@Schema(description = "위도", example = "37.5012") double lat,
			@Schema(description = "경도", example = "127.0381") double lng,
			@Schema(description = "카카오 식당의 이름(다시 찾은 값). 링크로 붙인 식당은 null(메뉴의 placeName을 쓴다)",
					example = "신전떡볶이 역삼점", nullable = true) String name,
			@Schema(description = "카카오 식당의 분류(가장 자세한 분류). 링크로 붙인 식당은 null", example = "떡볶이", nullable = true)
			String category,
			@Schema(description = "카카오 식당의 도로명 주소. 링크로 붙인 식당은 null", example = "서울 강남구 테헤란로 130",
					nullable = true) String roadAddress) {
	}
}
