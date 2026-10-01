package com.ohjumwhat.notice;

/**
 * 안 읽은 공지(상단 바 🔔의 점, 조직 홈 배너).
 *
 * @param latest 안 읽은 공지 중 가장 최근 것. 없으면 null
 */
public record UnreadNoticesResponse(long count, Latest latest) {

	/** @param version 업데이트 글의 버전. 개발자 노트는 null */
	public record Latest(Long id, NoticeKind kind, String version, String title) {
	}
}
