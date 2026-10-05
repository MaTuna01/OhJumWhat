package com.ohjumwhat.letter;

import java.time.Instant;

/**
 * 관리자 콘솔의 쪽지 신고. 익명 쪽지도 실제 보낸 사람을 보여준다(신고된 쪽지만 관리자가 본다).
 * @param senderId 실제 보낸 사람(강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 * @param recipientId 신고한 사람(= 받은 사람, 강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 * @param organizationName 쪽지를 보낸 조직(없어졌으면 null)
 * @param resolvedAt 처리 완료한 시각(처리 전이면 null)
 */
public record LetterReportResponse(Long id, Instant reportedAt, String reason, Instant resolvedAt,
		String resolvedByName, Long letterId, String body, boolean anonymous, Instant sentAt, Long organizationId,
		String organizationName, Long senderId, String senderName, String senderEmail, Long recipientId,
		String recipientName, String recipientEmail) {
}
