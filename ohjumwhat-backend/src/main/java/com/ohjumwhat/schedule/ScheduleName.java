package com.ohjumwhat.schedule;

import java.time.LocalDate;

/**
 * 정기 투표 규칙 이름의 날짜 토큰. 이름에 {@value #TODAY}를 넣으면 투표가 열릴 때
 * 그날의 한국 날짜("2026-10-08")로 바뀐다.
 * 토큰은 이것 하나뿐이고, 오타(`${오늘 날짜}`, `${날짜}`)는 바꾸지 않고 글자로 둔다.
 * 화면의 lib/scheduleName.ts와 규칙이 같아서 함께 고친다.
 */
final class ScheduleName {

	static final String TODAY = "${오늘날짜}";

	/** 이름 길이 한도. 토큰은 바뀐 날짜의 길이(10자)로 센다. */
	static final int MAX_LENGTH = 50;

	static final String TOO_LONG = "규칙 이름은 " + MAX_LENGTH + "자 이하로 입력해 주세요. 오늘 날짜는 10자로 세요.";

	private static final int DATE_LENGTH = "2026-10-08".length();

	private ScheduleName() {
	}

	/** 그날 열리는 투표의 제목 */
	static String render(String name, LocalDate date) {
		return name.replace(TODAY, date.toString());
	}

	/** 투표 제목이 됐을 때의 길이 */
	static int length(String name) {
		int tokens = (name.length() - name.replace(TODAY, "").length()) / TODAY.length();
		return name.length() + tokens * (DATE_LENGTH - TODAY.length());
	}
}
