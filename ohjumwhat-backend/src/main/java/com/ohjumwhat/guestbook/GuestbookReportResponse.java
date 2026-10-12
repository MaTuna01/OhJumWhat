package com.ohjumwhat.guestbook;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 콘솔의 방명록 신고. 제한·삭제된 글도 원문을 보여준다(신고된 글만 관리자가 본다).
 *
 * @param resolution 처리 결과(처리 전이면 null)
 * @param ownerId 방명록 주인 = 신고한 사람(강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 * @param authorId 쓴 사람(강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 */
@Schema(description = "관리자 콘솔의 방명록 신고(A09b). 제한·삭제된 글도 원문을 보여준다(신고된 글만 관리자가 본다)")
public record GuestbookReportResponse(@Schema(description = "신고 ID", example = "4") Long id,
		@Schema(description = "신고한 시각(UTC)", example = "2026-10-12T03:00:00Z") Instant reportedAt,
		@Schema(description = "신고 사유(선택, 100자)", example = "광고 글이에요", nullable = true) String reason,
		@Schema(description = "처리 결과. 처리 전이면 null", nullable = true) GuestbookReportResolution resolution,
		@Schema(description = "처리한 시각(UTC). 처리 전이면 null", example = "2026-10-12T05:00:00Z", nullable = true) Instant resolvedAt,
		@Schema(description = "처리한 관리자 이름(처리 전이거나 관리자가 탈퇴했으면 null)", example = "관리자", nullable = true)
		String resolvedByName,
		@Schema(description = "방명록 글 ID", example = "30") Long entryId,
		@Schema(description = "글 원문(제한·삭제돼도)", example = "광고 글입니다") String body,
		@Schema(description = "글을 쓴 시각(UTC)", example = "2026-10-11T02:30:00Z") Instant writtenAt,
		@Schema(description = "글을 지운 시각(UTC). 지우지 않았으면 null", nullable = true) Instant deletedAt,
		@Schema(description = "글을 제한한 시각(UTC). 제한하지 않았으면 null", nullable = true) Instant restrictedAt,
		@Schema(description = "방명록 주인 = 신고한 사람. 강제 탈퇴로 지워졌으면 null", example = "7", nullable = true) Long ownerId,
		@Schema(description = "주인 이름(별명, 없으면 구글 이름)", example = "김오점", nullable = true) String ownerName,
		@Schema(description = "주인 이메일", example = "kim@example.com", nullable = true) String ownerEmail,
		@Schema(description = "쓴 사람. 강제 탈퇴로 지워졌으면 null", example = "8", nullable = true) Long authorId,
		@Schema(description = "쓴 사람 이름", example = "이점심", nullable = true) String authorName,
		@Schema(description = "쓴 사람 이메일", example = "lee@example.com", nullable = true) String authorEmail) {
}
