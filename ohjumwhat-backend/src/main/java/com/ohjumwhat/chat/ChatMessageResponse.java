package com.ohjumwhat.chat;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

/**
 * 채팅 메시지. 같은 투표를 보는 모두에게 WebSocket으로도 보내므로 "내 글인지"는 넣지 않는다(화면이 author로 판단한다).
 *
 * @param author 쓴 사람(강제 탈퇴로 지워졌으면 null = "탈퇴한 사용자")
 * @param body 본문(지운 메시지는 null)
 * @param editedAt 마지막으로 고친 시각(없으면 null). 같은 메시지를 여러 번 받으면 더 늦은 쪽이 최신이다
 */
public record ChatMessageResponse(Long id, PersonResponse author, String body, Instant createdAt, Instant editedAt,
		boolean deleted) {

	static ChatMessageResponse of(ChatMessageRow row) {
		PersonResponse author = row.userId() == null ? null
				: new PersonResponse(row.userId(), row.name(), row.profileImageUrl());
		return new ChatMessageResponse(row.id(), author, row.body(), row.createdAt(), row.editedAt(),
				row.deletedAt() != null);
	}
}
