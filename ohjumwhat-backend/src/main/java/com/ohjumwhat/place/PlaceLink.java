package com.ohjumwhat.place;

/**
 * 메뉴(또는 조직)에 붙인 식당: 지도 링크와 식당 이름(선택). 식당이 없으면 이 값 대신 null을 쓴다.
 *
 * @param url 정리한 지도 링크(네이버 장소면 정식 링크)
 * @param name 사용자가 정한 식당 이름. 없으면 null
 */
public record PlaceLink(String url, String name) {
}
