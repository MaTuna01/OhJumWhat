package com.ohjumwhat.place;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지도 서비스 키(ohjumwhat.maps). 비어 있으면 그 기능을 끈다(지도·근처 식당 찾기 없이 링크 방식만 쓴다).
 *
 * @param kakaoRestKey 카카오 로컬 REST API 키(환경변수 KAKAO_REST_KEY). 서버에서만 쓴다
 * @param naverMapKeyId 네이버 지도(NCP Maps) Client ID(환경변수 NAVER_MAP_KEY_ID). 브라우저 스크립트 주소에 들어가는 공개 값이고,
 * NCP 콘솔에 등록한 도메인에서만 동작한다
 */
@ConfigurationProperties("ohjumwhat.maps")
public record MapsProperties(String kakaoRestKey, String naverMapKeyId) {

	public MapsProperties {
		kakaoRestKey = blankToNull(kakaoRestKey);
		naverMapKeyId = blankToNull(naverMapKeyId);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
