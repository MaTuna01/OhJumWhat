package com.ohjumwhat.place;

/**
 * 메뉴(또는 조직)에 붙인 식당. 식당이 없으면 이 값 대신 null을 쓴다. 두 가지다.
 * <ul>
 * <li>지도 링크(공유 링크): 이름·주소는 사용자가 붙여 넣은 공유 글에서 채우거나 직접 입력한 값이다(지도 API 결과가 아니다).</li>
 * <li>근처 식당 찾기(카카오 로컬)로 고른 식당: 약관상 장소 ID와 장소 링크만 저장할 수 있어서, 사용자가 친 검색어를 함께 두고
 * 이름·주소·위치는 볼 때마다 같은 검색어로 다시 찾는다.</li>
 * </ul>
 *
 * @param url 정리한 지도 링크(네이버 장소면 정식 링크, 카카오 식당이면 장소 링크)
 * @param name 사용자가 정한 식당 이름. 없으면 null(카카오 식당은 항상 null)
 * @param address 식당 주소(공유 글의 주소 줄). 없으면 null(카카오 식당은 항상 null)
 * @param kakaoPlaceId 카카오 장소 ID. 근처 식당 찾기로 고른 식당만
 * @param query 그 식당을 찾을 때 사용자가 친 검색어. 비면(null) 근처 둘러보기로 찾았다
 */
public record PlaceLink(String url, String name, String address, String kakaoPlaceId, String query) {

	static final String KAKAO_PLACE_URL = "https://place.map.kakao.com/";

	public PlaceLink(String url, String name) {
		this(url, name, null);
	}

	public PlaceLink(String url, String name, String address) {
		this(url, name, address, null, null);
	}

	/** 근처 식당 찾기로 고른 카카오 식당. 링크는 검증한 장소 ID로 서버가 만든다. */
	public static PlaceLink kakao(String kakaoPlaceId, String query) {
		return new PlaceLink(KAKAO_PLACE_URL + kakaoPlaceId, null, null, kakaoPlaceId, query);
	}
}
