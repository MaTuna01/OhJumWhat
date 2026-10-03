package com.ohjumwhat.menu;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

/**
 * @param author 쓴 사람(강제 탈퇴로 지워졌으면 null = "탈퇴한 사용자")
 * @param edited 고친 적이 있는지
 * @param mine 내가 쓴 댓글인지
 */
public record MenuCommentResponse(Long id, PersonResponse author, String body, Instant createdAt, boolean edited,
		boolean mine) {

	static MenuCommentResponse of(MenuCommentRow row, Long userId) {
		PersonResponse author = row.userId() == null ? null
				: new PersonResponse(row.userId(), row.name(), row.profileImageUrl());
		return new MenuCommentResponse(row.id(), author, row.body(), row.createdAt(), row.editedAt() != null,
				row.userId() != null && row.userId().equals(userId));
	}
}
