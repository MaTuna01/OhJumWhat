package com.ohjumwhat.user;

import com.ohjumwhat.common.ApiException;

/** 별명 입력 정리: 앞뒤 공백을 없애고 연속 공백은 하나로. 비면 null(구글 이름 사용) */
final class Nicknames {

	static final int MAX_LENGTH = 20;

	private Nicknames() {
	}

	static String normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String nickname = raw.strip().replaceAll("\\s+", " ");
		if (nickname.chars().anyMatch(Character::isISOControl)) {
			throw ApiException.badRequest("이름에 쓸 수 없는 문자가 있어요.");
		}
		// DB VARCHAR(20)은 글자(코드 포인트) 수로 센다.
		if (nickname.codePointCount(0, nickname.length()) > MAX_LENGTH) {
			throw ApiException.badRequest("이름은 20자 이하로 입력해 주세요.");
		}
		return nickname;
	}
}
