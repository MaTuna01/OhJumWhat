package com.ohjumwhat.menu;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param author 쓴 사람(강제 탈퇴로 지워졌으면 null = "탈퇴한 사용자")
 * @param edited 고친 적이 있는지
 * @param mine 내가 쓴 댓글인지
 */
@Schema(description = "메뉴 댓글 한 개")
public record MenuCommentResponse(
		@Schema(description = "댓글 ID", example = "500") Long id,
		@Schema(description = "쓴 사람. 강제 탈퇴로 지워졌으면 null(「탈퇴한 사용자」)", nullable = true) PersonResponse author,
		@Schema(description = "본문(한 줄, 200자)", example = "여기 국물 진짜 맛있어요") String body,
		@Schema(description = "쓴 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt,
		@Schema(description = "고친 적이 있는지(「수정됨」)", example = "false") boolean edited,
		@Schema(description = "내가 쓴 댓글인지(고치기·지우기 버튼)", example = "true") boolean mine) {

	static MenuCommentResponse of(MenuCommentRow row, Long userId) {
		PersonResponse author = row.userId() == null ? null
				: new PersonResponse(row.userId(), row.name(), row.profileImageUrl());
		return new MenuCommentResponse(row.id(), author, row.body(), row.createdAt(), row.editedAt() != null,
				row.userId() != null && row.userId().equals(userId));
	}
}
