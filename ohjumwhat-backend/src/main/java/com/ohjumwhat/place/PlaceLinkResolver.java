package com.ohjumwhat.place;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.ApiException;

/**
 * 붙여 넣은 링크를 저장할 링크로 바꾼다. naver.me 단축 링크는 리디렉션 주소에서 장소 ID를 꺼내 정식 링크로 바꾼다.
 * 네트워크를 쓰므로 DB 트랜잭션 밖(컨트롤러)에서 부른다. 네이버 확인에 실패해도 단축 링크를 그대로 두고 오류를 내지 않는다.
 */
@Component
public class PlaceLinkResolver {

	private final NaverShortLinks naverShortLinks;

	public PlaceLinkResolver(NaverShortLinks naverShortLinks) {
		this.naverShortLinks = naverShortLinks;
	}

	/** 링크와 식당 이름. 링크가 비면 null(식당 없음, 이름도 버린다). 주소·이름이 올바르지 않으면 400 */
	public PlaceLink place(String rawLink, String rawName) {
		return place(rawLink, rawName, null);
	}

	/**
	 * 식당: 근처 식당 찾기로 고른 카카오 식당(kakaoPlaceId)이나 지도 링크. 둘 다 비면 null(식당 없음).
	 * 둘을 함께 보내거나 값이 올바르지 않으면 400
	 */
	public PlaceLink place(String rawLink, String rawName, String rawAddress, String rawKakaoPlaceId, String rawQuery) {
		String kakaoPlaceId = PlaceLinks.kakaoPlaceId(rawKakaoPlaceId);
		if (kakaoPlaceId == null) {
			return place(rawLink, rawName, rawAddress);
		}
		if (rawLink != null && !rawLink.isBlank()) {
			throw ApiException.badRequest("지도 링크와 근처 식당을 함께 붙일 수 없어요.");
		}
		return PlaceLink.kakao(kakaoPlaceId, PlaceLinks.query(rawQuery));
	}

	/** 링크와 식당 이름·주소. 링크가 비면 null(식당 없음, 이름·주소도 버린다). 값이 올바르지 않으면 400 */
	public PlaceLink place(String rawLink, String rawName, String rawAddress) {
		String name = PlaceLinks.name(rawName);
		String address = PlaceLinks.address(rawAddress);
		String url = resolve(rawLink);
		return url == null ? null : new PlaceLink(url, name, address);
	}

	/** 저장할 링크. 빈 값이면 null */
	public String resolve(String rawLink) {
		String link = PlaceLinks.normalize(rawLink);
		return PlaceLinks.naverShortCode(link)
			.flatMap(naverShortLinks::location)
			.filter(PlaceLinkResolver::isNaver)
			.flatMap(PlaceLinks::naverPlaceUrl)
			.orElse(link);
	}

	/** 네이버 주소(*.naver.com)로 보낸 리디렉션만 믿는다. */
	private static boolean isNaver(String location) {
		try {
			String host = Optional.ofNullable(URI.create(location).getHost()).orElse("").toLowerCase(Locale.ROOT);
			return host.endsWith(".naver.com");
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}
}
