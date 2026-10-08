package com.ohjumwhat.chat;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

/**
 * 채팅 메시지. 같은 투표를 보는 모두에게 WebSocket으로도 보내므로 "내 글인지"는 넣지 않는다(화면이 author로 판단한다).
 *
 * @param author 쓴 사람(강제 탈퇴로 지워졌으면 null = "탈퇴한 사용자")
 * @param body 본문(사진 메시지·지운 메시지는 null)
 * @param photo 사진(글 메시지·지운 메시지는 null)
 * @param editedAt 마지막으로 고친 시각(없으면 null). 같은 메시지를 여러 번 받으면 더 늦은 쪽이 최신이다
 */
public record ChatMessageResponse(Long id, PersonResponse author, String body, Photo photo, Instant createdAt,
		Instant editedAt, boolean deleted) {

	static final String PHOTO_PATH = "/api/chat-photos/";

	/**
	 * 사진 메시지의 사진. 보관 기간(30일)이 지나도 사진 메시지인 것은 남는다(고치기 숨김, 미리보기 「📷 사진」).
	 *
	 * @param width 원본 크기(px). 화면이 사진이 뜨기 전에 자리를 잡는다
	 * @param expired 보관 기간이 지났으면 true(주소는 null)
	 * @param url 원본(긴 변 1600px) 주소
	 * @param thumbnailUrl 썸네일(긴 변 480px) 주소
	 */
	public record Photo(int width, int height, boolean expired, String url, String thumbnailUrl) {
	}

	static ChatMessageResponse of(ChatMessageRow row, Instant now) {
		PersonResponse author = row.userId() == null ? null
				: new PersonResponse(row.userId(), row.name(), row.profileImageUrl());
		return new ChatMessageResponse(row.id(), author, row.body(), photo(row, now), row.createdAt(), row.editedAt(),
				row.deletedAt() != null);
	}

	private static Photo photo(ChatMessageRow row, Instant now) {
		if (row.imageKey() == null) {
			return null;
		}
		if (ChatMessage.isPhotoExpired(row.createdAt(), now)) {
			return new Photo(row.imageWidth(), row.imageHeight(), true, null, null);
		}
		return new Photo(row.imageWidth(), row.imageHeight(), false, PHOTO_PATH + row.imageKey() + ".jpg",
				PHOTO_PATH + row.imageKey() + "_t.jpg");
	}
}
