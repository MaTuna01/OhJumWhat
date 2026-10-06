package com.ohjumwhat.push;

import java.util.Map;

/**
 * 기기로 보낼 푸시 한 건. 알림 문구는 서비스 워커가 data로 직접 그린다(notification 블록 없이 data만 보낸다).
 * 본문(방명록·쪽지 내용)은 절대 싣지 않는다.
 *
 * @param kind 종류(열 화면과 알림 묶음)
 * @param title 알림 제목(PushMessages가 만든다)
 */
public record PushMessage(PushKind kind, String title) {

	/** 푸시 data: kind, title, url, tag(모두 문자열) */
	public Map<String, String> data() {
		return Map.of("kind", kind.name(), "title", title, "url", kind.url(), "tag", kind.tag());
	}
}
