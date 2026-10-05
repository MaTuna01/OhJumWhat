package com.ohjumwhat.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ohjumwhat.common.ApiException;

/**
 * 프로필 소개 입력 정리: 한줄 소개와 좋아하는 음식(태그). 화면의 lib/profile.ts와 같은 규칙이다.
 * 글자 수는 DB VARCHAR처럼 글자(코드 포인트) 수로 센다.
 */
final class ProfileIntro {

	static final int BIO_MAX_LENGTH = 50;

	static final int FOOD_TAG_MAX_LENGTH = 10;

	static final int FOOD_TAGS_MAX = 3;

	private ProfileIntro() {
	}

	/** 앞뒤 공백을 없애고 연속 공백은 하나로. 비면 null(소개 지우기) */
	static String bio(String raw) {
		String bio = singleLine(raw);
		if (bio.isEmpty()) {
			return null;
		}
		if (hasControl(bio)) {
			throw ApiException.badRequest("한줄 소개에 쓸 수 없는 문자가 있어요.");
		}
		if (length(bio) > BIO_MAX_LENGTH) {
			throw ApiException.badRequest("한줄 소개는 " + BIO_MAX_LENGTH + "자 이하로 입력해 주세요.");
		}
		return bio;
	}

	/**
	 * 태그마다 공백과 앞의 #을 정리하고 빈 태그는 뺀다. 띄어쓰기·대소문자만 다른 태그는 먼저 적은 것만 남긴다
	 * (메뉴 통계의 「김치찌개 = 김치 찌개」와 같은 기준). 적은 순서는 그대로 둔다.
	 */
	static List<String> foodTags(List<String> raw) {
		List<String> tags = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (String rawTag : raw == null ? List.<String>of() : raw) {
			String tag = singleLine(rawTag == null ? "" : rawTag.strip().replaceFirst("^#+", ""));
			if (tag.isEmpty()) {
				continue;
			}
			if (hasControl(tag)) {
				throw ApiException.badRequest("음식 이름에 쓸 수 없는 문자가 있어요.");
			}
			if (length(tag) > FOOD_TAG_MAX_LENGTH) {
				throw ApiException.badRequest("음식 이름은 " + FOOD_TAG_MAX_LENGTH + "자 이하로 입력해 주세요.");
			}
			if (keys.add(tag.replace(" ", "").toLowerCase(Locale.ROOT))) {
				tags.add(tag);
			}
		}
		if (tags.size() > FOOD_TAGS_MAX) {
			throw ApiException.badRequest("좋아하는 음식은 " + FOOD_TAGS_MAX + "개까지 적을 수 있어요.");
		}
		return tags;
	}

	private static String singleLine(String raw) {
		return raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
	}

	private static boolean hasControl(String text) {
		return text.chars().anyMatch(Character::isISOControl);
	}

	private static int length(String text) {
		return text.codePointCount(0, text.length());
	}
}
