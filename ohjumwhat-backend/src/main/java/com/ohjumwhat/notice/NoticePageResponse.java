package com.ohjumwhat.notice;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 「새 소식」 한 페이지(최신순).
 *
 * @param hasMore 다음 페이지가 있는지(「더 보기」)
 */
@Schema(description = "「새 소식」 한 쪽(게시 시각 최신순 10개)")
public record NoticePageResponse(@Schema(description = "공지(최신순)") List<Item> notices,
		@Schema(description = "다음 쪽이 있는지(「더 보기」)", example = "true") boolean hasMore) {

	/**
	 * @param version 업데이트 글의 버전(예: 1.5.0). 개발자 노트는 null
	 * @param unread 안 읽은 공지인지(NEW). 이 요청 시점의 읽음 기준으로 계산한다.
	 */
	@Schema(name = "NoticeItem", description = "공지 하나")
	public record Item(@Schema(description = "공지 ID", example = "12") Long id,
			NoticeKind kind,
			@Schema(description = "업데이트 글의 버전. 개발자 노트는 null", example = "1.11.6", nullable = true) String version,
			@Schema(description = "제목(100자)", example = "채팅에서 사진을 보낼 수 있어요") String title,
			@Schema(description = "본문(일반 텍스트: 빈 줄 = 문단, \"- \" = 목록, http(s) 주소 = 링크. HTML·마크다운은 해석하지 않는다)",
					example = "채팅 입력창의 📷로 사진을 보낼 수 있어요.\n\n- 한 번에 5장까지")
			String body,
			@Schema(description = "게시 시각(UTC). 고쳐도 바뀌지 않는다", example = "2026-10-12T02:30:00Z") Instant publishedAt,
			@Schema(description = "안 읽은 공지인지(NEW). 이 요청 시점의 읽음 기준", example = "true") boolean unread) {
	}
}
