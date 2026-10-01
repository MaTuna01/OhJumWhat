package com.ohjumwhat.notice;

import java.time.Instant;
import java.util.List;

/**
 * 「새 소식」 한 페이지(최신순).
 *
 * @param hasMore 다음 페이지가 있는지(「더 보기」)
 */
public record NoticePageResponse(List<Item> notices, boolean hasMore) {

	/**
	 * @param version 업데이트 글의 버전(예: 1.5.0). 개발자 노트는 null
	 * @param unread 안 읽은 공지인지(NEW). 이 요청 시점의 읽음 기준으로 계산한다.
	 */
	public record Item(Long id, NoticeKind kind, String version, String title, String body, Instant publishedAt,
			boolean unread) {
	}
}
