package com.ohjumwhat.place;

/**
 * 메뉴(또는 조직)에 붙인 식당: 지도 링크와 식당 이름·주소(선택). 식당이 없으면 이 값 대신 null을 쓴다.
 * 이름·주소는 사용자가 붙여 넣은 공유 글에서 채우거나 직접 입력한 값이다(지도 API 결과가 아니다).
 *
 * @param url 정리한 지도 링크(네이버 장소면 정식 링크)
 * @param name 사용자가 정한 식당 이름. 없으면 null
 * @param address 식당 주소(공유 글의 주소 줄). 지도에 올릴 때 볼 때마다 좌표로 바꾼다. 없으면 null
 */
public record PlaceLink(String url, String name, String address) {

	public PlaceLink(String url, String name) {
		this(url, name, null);
	}
}
