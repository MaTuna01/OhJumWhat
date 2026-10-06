package com.ohjumwhat.push;

/**
 * 푸시 종류. 알림을 누르면 열 화면(url)과 알림 묶음(tag)을 여기 한 곳에서 정한다. 같은 tag의 알림은 기기에서 하나로 바뀌고,
 * 전달되지 않은 채 기다리는 푸시도 새 푸시로 바뀐다(Web Push Topic 헤더).
 */
public enum PushKind {

	/** 내 방명록에 새 글 */
	GUESTBOOK("/me#guestbook", "ohjumwhat-guestbook"),

	/** 내가 남긴 방명록 글을 관리자가 제한했다(앱을 열면 경고 안내 창이 뜬다) */
	GUESTBOOK_RESTRICTED("/me", "ohjumwhat-guestbook-restricted"),

	/** 새 쪽지·답장 */
	LETTER("/letters", "ohjumwhat-letter");

	private final String url;

	private final String tag;

	PushKind(String url, String tag) {
		this.url = url;
		this.tag = tag;
	}

	public String url() {
		return url;
	}

	public String tag() {
		return tag;
	}
}
