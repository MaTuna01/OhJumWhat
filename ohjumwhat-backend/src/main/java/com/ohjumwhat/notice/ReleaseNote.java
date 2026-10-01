package com.ohjumwhat.notice;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * 업데이트 글 파일(src/main/resources/release-notes/{버전}.md) 한 개.
 * 첫 줄은 "# 제목", 나머지는 본문(일반 텍스트: 빈 줄 = 문단, "- " = 목록)이다.
 */
public record ReleaseNote(String version, String title, String body) {

	private static final Pattern FILE_NAME = Pattern.compile("\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}\\.md");

	private static final int TITLE_MAX = 100;

	private static final int BODY_MAX = 5000;

	/** 버전 오름차순. 글자 순서가 아니라 숫자 순서라 1.10.0이 1.9.0 뒤에 온다. */
	public static final Comparator<ReleaseNote> BY_VERSION = (a, b) -> compareVersions(a.version(), b.version());

	/**
	 * 파일 하나를 읽는다.
	 * @throws IllegalArgumentException 형식이 맞지 않으면(메시지에 파일 이름과 이유)
	 */
	public static ReleaseNote parse(String fileName, String content) {
		if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
			throw invalid(fileName, "파일 이름은 1.5.0.md처럼 버전이어야 합니다");
		}
		String version = fileName.substring(0, fileName.length() - ".md".length());
		String text = content.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').strip();
		int newline = text.indexOf('\n');
		String firstLine = newline < 0 ? text : text.substring(0, newline);
		if (!firstLine.startsWith("# ")) {
			throw invalid(fileName, "첫 줄은 \"# 제목\"이어야 합니다");
		}
		String title = firstLine.substring(2).strip();
		String body = newline < 0 ? "" : text.substring(newline + 1).strip();
		if (title.isEmpty() || title.length() > TITLE_MAX) {
			throw invalid(fileName, "제목은 1~" + TITLE_MAX + "자여야 합니다");
		}
		if (body.isEmpty() || body.length() > BODY_MAX) {
			throw invalid(fileName, "본문은 1~" + BODY_MAX + "자여야 합니다");
		}
		return new ReleaseNote(version, title, body);
	}

	static int compareVersions(String a, String b) {
		String[] left = a.split("\\.");
		String[] right = b.split("\\.");
		for (int i = 0; i < Math.min(left.length, right.length); i++) {
			int compared = Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]));
			if (compared != 0) {
				return compared;
			}
		}
		return Integer.compare(left.length, right.length);
	}

	private static IllegalArgumentException invalid(String fileName, String reason) {
		return new IllegalArgumentException(fileName + ": " + reason);
	}
}
