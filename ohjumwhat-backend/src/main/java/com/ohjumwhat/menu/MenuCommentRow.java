package com.ohjumwhat.menu;

import java.time.Instant;

/** 댓글 조회 행. 쓴 사람이 강제 탈퇴로 지워졌으면 userId·name·profileImageUrl이 null이다. */
public record MenuCommentRow(Long id, Long userId, String name, String profileImageUrl, String body,
		Instant createdAt, Instant editedAt) {
}
