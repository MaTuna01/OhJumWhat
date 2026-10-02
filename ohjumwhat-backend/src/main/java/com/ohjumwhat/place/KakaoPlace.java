package com.ohjumwhat.place;

import java.util.List;

/**
 * 카카오 로컬에서 찾은 음식점. 약관상 id(장소 ID) 말고는 저장하지 않고 화면에 보여줄 때만 쓴다.
 *
 * @param category 가장 자세한 분류(예: "음식점 > 한식 > 찌개,전골" → "찌개,전골")
 * @param roadAddress 도로명 주소(없으면 지번 주소)
 * @param distance 검색 기준점(회사)에서의 직선거리(m). 모르면 null
 */
public record KakaoPlace(String id, String name, String category, String roadAddress, Coordinate at, Integer distance) {

	/**
	 * 검색 결과 한 페이지
	 *
	 * @param end 마지막 페이지인지
	 */
	public record Page(List<KakaoPlace> places, boolean end) {
	}
}
