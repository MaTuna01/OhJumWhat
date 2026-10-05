package com.ohjumwhat.user;

import java.util.List;

import com.ohjumwhat.common.ApiException;

/**
 * 프로필 소개 입력 정리: 한줄 소개와 좋아하는 음식(태그). 화면의 lib/profile.ts와 같은 규칙이다.
 * 글자 수는 DB VARCHAR처럼 글자(코드 포인트) 수로 센다.
 */
final class ProfileIntro {

	static final int BIO_MAX_LENGTH = 50;

	static final int FOOD_TAG_MAX_LENGTH = 10;

	static final int FOOD_TAGS_MAX = 3;

	private static final ProfileText.TagRule FOOD_TAG_RULE = new ProfileText.TagRule(FOOD_TAG_MAX_LENGTH,
			FOOD_TAGS_MAX, "음식 이름에 쓸 수 없는 문자가 있어요.", "음식 이름은 " + FOOD_TAG_MAX_LENGTH + "자 이하로 입력해 주세요.",
			"좋아하는 음식은 " + FOOD_TAGS_MAX + "개까지 적을 수 있어요.");

	private ProfileIntro() {
	}

	/** 앞뒤 공백을 없애고 연속 공백은 하나로. 비면 null(소개 지우기) */
	static String bio(String raw) {
		String bio = ProfileText.singleLine(raw);
		if (bio.isEmpty()) {
			return null;
		}
		if (ProfileText.hasControl(bio)) {
			throw ApiException.badRequest("한줄 소개에 쓸 수 없는 문자가 있어요.");
		}
		if (ProfileText.length(bio) > BIO_MAX_LENGTH) {
			throw ApiException.badRequest("한줄 소개는 " + BIO_MAX_LENGTH + "자 이하로 입력해 주세요.");
		}
		return bio;
	}

	/** 좋아하는 음식(태그) 정리. 규칙은 {@link ProfileText#tags}. 비면 음식을 지운다. */
	static List<String> foodTags(List<String> raw) {
		return ProfileText.tags(raw, FOOD_TAG_RULE);
	}
}
