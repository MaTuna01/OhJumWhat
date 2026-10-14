package com.ohjumwhat.guestbook;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 방명록 글 한 줄(보는 사람 기준).
 *
 * @param author 쓴 사람(강제 탈퇴했으면 null, 화면은 「탈퇴한 사용자」)
 * @param body 본문(제한된 글이면 null, 누구에게도 보내지 않는다)
 * @param mine 내가 쓴 글
 * @param canDelete 지울 수 있는지(쓴 사람 또는 주인)
 * @param canReport 신고할 수 있는지(주인이고, 아직 신고하지 않았고, 제한되지 않은 글)
 * @param reported 주인이 이미 신고했는지(주인에게만 계산하고, 주인이 아니면 언제나 false)
 */
@Schema(description = "방명록 글 한 줄(보는 사람 기준)")
public record GuestbookEntryResponse(@Schema(description = "글 ID", example = "30") Long id,
		@Schema(description = "쓴 사람(다른 조직 사람이어도 보인다). 강제 탈퇴했으면 null(「탈퇴한 사용자」)", nullable = true)
		PersonResponse author,
		@Schema(description = "본문(한 줄 100자). 관리자가 제한한 글이면 null(화면은 「관리자에 의해 제한된 게시글입니다」)",
				example = "지난번 추천해 준 김치찌개 맛있었어요!", nullable = true)
		String body,
		@Schema(description = "쓴 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt,
		@Schema(description = "관리자가 제한한 글인지", example = "false") boolean restricted,
		@Schema(description = "내가 쓴 글인지", example = "false") boolean mine,
		@Schema(description = "지울 수 있는지(주인, 또는 제한되지 않은 내 글)", example = "true") boolean canDelete,
		@Schema(description = "신고할 수 있는지(주인이고, 아직 신고하지 않았고, 제한되지 않은 글)", example = "true") boolean canReport,
		@Schema(description = "주인이 이미 신고했는지(주인이 아니면 언제나 false)", example = "false") boolean reported) {
}
