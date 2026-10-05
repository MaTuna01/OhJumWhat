package com.ohjumwhat.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.ohjumwhat.common.ApiException;

/**
 * 프로필 입력(소개·상세 프로필)이 함께 쓰는 정리 규칙. 화면의 lib/profile.ts와 같은 규칙이다.
 * 글자 수는 DB VARCHAR처럼 글자(코드 포인트) 수로 센다.
 */
final class ProfileText {

	/** 화면(JS의 \s)처럼 유니코드 공백(전각 공백, NBSP 등)도 공백으로 본다. */
	private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	private ProfileText() {
	}

	/**
	 * 직접 적는 태그(좋아하는 음식, 취미)의 규칙
	 * @param maxLength 한 개의 최대 글자 수
	 * @param max 최대 개수
	 */
	record TagRule(int maxLength, int max, String controlMessage, String lengthMessage, String tooManyMessage) {
	}

	/** 앞뒤 공백을 없애고 연속 공백은 하나로 */
	static String singleLine(String raw) {
		return raw == null ? "" : SPACES.matcher(raw.strip()).replaceAll(" ");
	}

	static boolean hasControl(String text) {
		return text.chars().anyMatch(Character::isISOControl);
	}

	static int length(String text) {
		return text.codePointCount(0, text.length());
	}

	/**
	 * 태그마다 공백과 앞의 #을 정리하고 빈 태그는 뺀다. 띄어쓰기·대소문자만 다른 태그는 먼저 적은 것만 남긴다
	 * (메뉴 통계의 「김치찌개 = 김치 찌개」와 같은 기준). 적은 순서는 그대로 둔다.
	 */
	static List<String> tags(List<String> raw, TagRule rule) {
		List<String> tags = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (String rawTag : raw == null ? List.<String>of() : raw) {
			String tag = singleLine(rawTag == null ? "" : rawTag.strip().replaceFirst("^#+", ""));
			if (tag.isEmpty()) {
				continue;
			}
			if (hasControl(tag)) {
				throw ApiException.badRequest(rule.controlMessage());
			}
			if (length(tag) > rule.maxLength()) {
				throw ApiException.badRequest(rule.lengthMessage());
			}
			if (keys.add(tag.replace(" ", "").toLowerCase(Locale.ROOT))) {
				tags.add(tag);
			}
		}
		if (tags.size() > rule.max()) {
			throw ApiException.badRequest(rule.tooManyMessage());
		}
		return tags;
	}
}
