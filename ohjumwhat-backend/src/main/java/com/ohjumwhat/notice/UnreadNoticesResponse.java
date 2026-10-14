package com.ohjumwhat.notice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 안 읽은 공지(상단 바 🔔의 점, 조직 홈 배너).
 *
 * @param latest 안 읽은 공지 중 가장 최근 것. 없으면 null
 */
@Schema(description = "안 읽은 공지(상단 바 종의 점, 조직 홈 배너)")
public record UnreadNoticesResponse(@Schema(description = "안 읽은 공지 수", example = "1") long count,
		@Schema(description = "안 읽은 공지 중 가장 최근 것(배너에 보여준다). 없으면 null", nullable = true) Latest latest) {

	/** @param version 업데이트 글의 버전. 개발자 노트는 null */
	@Schema(name = "LatestNotice", description = "안 읽은 공지 중 가장 최근 것")
	public record Latest(@Schema(description = "공지 ID", example = "12") Long id,
			NoticeKind kind,
			@Schema(description = "업데이트 글의 버전. 개발자 노트는 null", example = "1.11.6", nullable = true) String version,
			@Schema(description = "제목", example = "채팅에서 사진을 보낼 수 있어요") String title) {
	}
}
