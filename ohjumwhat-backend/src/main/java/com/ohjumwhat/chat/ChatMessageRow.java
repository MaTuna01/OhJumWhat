package com.ohjumwhat.chat;

import java.time.Instant;

/** 채팅 조회 행. 쓴 사람이 강제 탈퇴로 지워졌으면 userId·name·profileImageUrl이 null이다. */
public record ChatMessageRow(Long id, Long userId, String name, String profileImageUrl, String body, Instant createdAt,
		Instant editedAt, Instant deletedAt) {
}
