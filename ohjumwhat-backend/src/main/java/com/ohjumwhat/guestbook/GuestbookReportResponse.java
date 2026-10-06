package com.ohjumwhat.guestbook;

import java.time.Instant;

/**
 * 관리자 콘솔의 방명록 신고. 제한·삭제된 글도 원문을 보여준다(신고된 글만 관리자가 본다).
 *
 * @param resolution 처리 결과(처리 전이면 null)
 * @param ownerId 방명록 주인 = 신고한 사람(강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 * @param authorId 쓴 사람(강제 탈퇴로 지워졌으면 null, 이름·이메일도 null)
 */
public record GuestbookReportResponse(Long id, Instant reportedAt, String reason,
		GuestbookReportResolution resolution, Instant resolvedAt, String resolvedByName, Long entryId, String body,
		Instant writtenAt, Instant deletedAt, Instant restrictedAt, Long ownerId, String ownerName, String ownerEmail,
		Long authorId, String authorName, String authorEmail) {
}
