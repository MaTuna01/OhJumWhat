package com.ohjumwhat.letter;

/** 쪽지 첫 줄 미리보기: 빈 줄이 아닌 첫 줄을 40자(코드 포인트)까지. 넘으면 …을 붙인다. 쪽지 본문 전체를 다시 보내지 않으려고 쓴다. */
final class LetterPreview {

	static final int MAX_LENGTH = 40;

	private LetterPreview() {
	}

	static String of(String body) {
		if (body == null) {
			return null;
		}
		String line = body.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
		if (line.codePointCount(0, line.length()) <= MAX_LENGTH) {
			return line;
		}
		return line.substring(0, line.offsetByCodePoints(0, MAX_LENGTH)) + "…";
	}
}
