package com.ohjumwhat.menu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ohjumwhat.common.ApiException;

/**
 * 메뉴의 식당 지도 링크. 지도 서비스를 가리지 않고 http/https 주소면 받는다.
 * 지도 앱의 "공유" 문구("[네이버 지도] 김밥천국 https://naver.me/...")를 통째로 붙여도 주소만 꺼내 쓴다.
 */
final class MenuLinks {

	static final int MAX_LENGTH = 500;

	private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

	/** 도메인에 점이 있는 http/https 주소(스킴은 대소문자 구분 없음) */
	private static final Pattern VALID = Pattern.compile(
			"^https?://[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(:\\d{1,5})?([/?#]\\S*)?$", Pattern.CASE_INSENSITIVE);

	private MenuLinks() {
	}

	/** 빈 값이면 null(링크 없음). 스킴이 없으면 https://를 붙인다. 주소가 아니면 400. */
	static String normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Matcher matcher = URL_IN_TEXT.matcher(raw);
		String link = matcher.find() ? matcher.group() : raw.strip();
		if (!link.contains("://")) {
			link = "https://" + link;
		}
		if (link.length() > MAX_LENGTH) {
			throw ApiException.badRequest("링크는 500자 이하로 입력해 주세요.");
		}
		if (!VALID.matcher(link).matches()) {
			throw ApiException.badRequest("링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요.");
		}
		return link;
	}
}
