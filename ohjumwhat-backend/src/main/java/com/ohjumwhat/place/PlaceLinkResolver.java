package com.ohjumwhat.place;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

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
		String name = PlaceLinks.name(rawName);
		String url = resolve(rawLink);
		return url == null ? null : new PlaceLink(url, name);
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
