package com.ohjumwhat.letter;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 콘솔의 쪽지 신고. 익명 쪽지도 실제 보낸 사람을 보여준다(신고된 쪽지만 관리자가 본다).
 * @param senderId 실제 보낸 사람(강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 * @param recipientId 신고한 사람(= 받은 사람, 강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 * @param organizationName 쪽지를 보낸 조직(없어졌으면 null)
 * @param resolvedAt 처리 완료한 시각(처리 전이면 null)
 */
@Schema(description = "관리자 콘솔의 쪽지 신고(A09). 익명 쪽지도 실제 보낸 사람을 보여준다(신고된 쪽지만 관리자가 본다)")
public record LetterReportResponse(@Schema(description = "신고 ID", example = "3") Long id,
		@Schema(description = "신고한 시각(UTC)", example = "2026-10-12T03:00:00Z") Instant reportedAt,
		@Schema(description = "신고 사유(선택, 100자)", example = "욕설이 있어요", nullable = true) String reason,
		@Schema(description = "처리 완료한 시각(UTC). 처리 전이면 null", example = "2026-10-12T05:00:00Z", nullable = true)
		Instant resolvedAt,
		@Schema(description = "처리한 관리자 이름(처리 전이거나 관리자가 탈퇴했으면 null)", example = "관리자", nullable = true)
		String resolvedByName,
		@Schema(description = "쪽지 ID", example = "120") Long letterId,
		@Schema(description = "쪽지 본문", example = "오늘 점심 같이 가요!") String body,
		@Schema(description = "익명으로 보낸 쪽지인지", example = "true") boolean anonymous,
		@Schema(description = "쪽지를 보낸 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant sentAt,
		@Schema(description = "쪽지를 보낸 조직 ID(없어졌으면 null)", example = "1", nullable = true) Long organizationId,
		@Schema(description = "쪽지를 보낸 조직 이름(없어졌으면 null)", example = "개발팀", nullable = true) String organizationName,
		@Schema(description = "실제 보낸 사람(익명이어도). 강제 탈퇴로 지워졌으면 null", example = "7", nullable = true) Long senderId,
		@Schema(description = "보낸 사람 이름(별명, 없으면 구글 이름)", example = "김오점", nullable = true) String senderName,
		@Schema(description = "보낸 사람 이메일", example = "kim@example.com", nullable = true) String senderEmail,
		@Schema(description = "신고한 사람(= 받은 사람). 강제 탈퇴로 지워졌으면 null", example = "8", nullable = true) Long recipientId,
		@Schema(description = "받은 사람 이름", example = "이점심", nullable = true) String recipientName,
		@Schema(description = "받은 사람 이메일", example = "lee@example.com", nullable = true) String recipientEmail) {
}
