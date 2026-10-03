package com.ohjumwhat.common;

/** 사용자가 쓴 글(댓글 등) 정리: 앞뒤 공백을 지우고, 제어 문자를 막고, 글자 수를 확인한다. */
public final class UserText {

	private UserText() {
	}

	/**
	 * @param maxLength 최대 글자 수. DB VARCHAR처럼 글자(코드 포인트) 수로 센다
	 * @param multiline 줄바꿈(\n)을 허용할지. \r\n은 \n으로 바꾼다
	 * @return 정리한 글(비어 있으면 400)
	 */
	public static String normalize(String raw, int maxLength, boolean multiline) {
		String text = raw == null ? "" : raw.replace("\r\n", "\n").strip();
		if (text.isEmpty()) {
			throw ApiException.badRequest("내용을 입력해 주세요.");
		}
		if (text.chars().anyMatch(c -> Character.isISOControl(c) && !(multiline && c == '\n'))) {
			throw ApiException.badRequest("쓸 수 없는 문자가 있어요.");
		}
		if (text.codePointCount(0, text.length()) > maxLength) {
			throw ApiException.badRequest(maxLength + "자 이하로 입력해 주세요.");
		}
		return text;
	}
}
