package com.ohjumwhat.place;

import java.util.List;
import java.util.Locale;

/**
 * 카카오 로컬에서 찾은 음식점. 약관상 id(장소 ID) 말고는 저장하지 않고 화면에 보여줄 때만 쓴다.
 *
 * @param categories 분류 경로에서 「음식점」을 뺀 것(예: "음식점 > 분식 > 떡볶이 > 두끼떡볶이" → [분식, 떡볶이, 두끼떡볶이])
 * @param roadAddress 도로명 주소(없으면 지번 주소)
 * @param distance 검색 기준점(회사)에서의 직선거리(m). 모르면 null
 */
public record KakaoPlace(String id, String name, List<String> categories, String roadAddress, Coordinate at,
		Integer distance) {

	public KakaoPlace {
		categories = categories == null ? List.of() : List.copyOf(categories);
	}

	/**
	 * 화면에 보여줄 분류: 가장 자세한 분류. 마지막 분류가 상호(체인 이름)면 그 앞의 분류를 쓴다.
	 * 예) [치킨, BHC치킨] + "BHC치킨 가산점" → "치킨", [분식, 떡볶이, 두끼떡볶이] → "떡볶이", [한식, 찌개,전골] → "찌개,전골"
	 */
	public String category() {
		if (categories.isEmpty()) {
			return null;
		}
		String last = categories.getLast();
		if (categories.size() > 1 && compact(name).contains(compact(last))) {
			return categories.get(categories.size() - 2);
		}
		return last;
	}

	/** 검색어가 이름이나 분류에 들어 있는지(띄어쓰기·대소문자 무시). 아니면 메뉴·태그로만 걸린 곳이다. */
	public boolean matches(String query) {
		String q = compact(query);
		return compact(name).contains(q) || categories.stream().anyMatch(c -> compact(c).contains(q));
	}

	private static String compact(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
	}

	/**
	 * 검색 결과 한 페이지
	 *
	 * @param end 마지막 페이지인지
	 */
	public record Page(List<KakaoPlace> places, boolean end) {
	}
}
