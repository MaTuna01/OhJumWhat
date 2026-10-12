package com.ohjumwhat.chat;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채팅 메시지. 같은 투표를 보는 모두에게 WebSocket으로도 보내므로 "내 글인지"는 넣지 않는다(화면이 author로 판단한다).
 *
 * @param author 쓴 사람(강제 탈퇴로 지워졌으면 null = "탈퇴한 사용자")
 * @param body 본문(사진 메시지·지운 메시지는 null)
 * @param photo 사진(글 메시지·지운 메시지는 null)
 * @param editedAt 마지막으로 고친 시각(없으면 null). 같은 메시지를 여러 번 받으면 더 늦은 쪽이 최신이다
 */
@Schema(description = "채팅 메시지. WebSocket으로 받는 message도 같은 모양이다. 「내 글인지」는 없고 화면이 author.userId로 판단한다. "
		+ "지우지 않은 메시지는 body와 photo 중 하나만 있다")
public record ChatMessageResponse(
		@Schema(description = "메시지 ID(보낸 순서대로 커진다)", example = "1234") Long id,
		@Schema(description = "쓴 사람. 강제 탈퇴로 지워졌으면 null(「탈퇴한 사용자」)", nullable = true) PersonResponse author,
		@Schema(description = "본문(300자, 줄바꿈 포함). 사진 메시지·지운 메시지는 null", example = "저 5분 늦어요!", nullable = true)
		String body,
		@Schema(description = "사진. 글 메시지·지운 메시지는 null", nullable = true) Photo photo,
		@Schema(description = "보낸 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt,
		@Schema(description = "마지막으로 고친 시각(UTC, 없으면 null). 같은 메시지를 여러 번 받으면 더 늦은 쪽이 최신이다",
				example = "2026-10-12T02:31:10Z", nullable = true) Instant editedAt,
		@Schema(description = "지운 메시지인지(「삭제된 메시지예요」, body·photo 없음)", example = "false") boolean deleted) {

	static final String PHOTO_PATH = "/api/chat-photos/";

	/**
	 * 사진 메시지의 사진. 보관 기간(30일)이 지나도 사진 메시지인 것은 남는다(고치기 숨김, 미리보기 「📷 사진」).
	 *
	 * @param width 원본 크기(px). 화면이 사진이 뜨기 전에 자리를 잡는다
	 * @param expired 보관 기간이 지났으면 true(주소는 null)
	 * @param url 원본(긴 변 1600px) 주소
	 * @param thumbnailUrl 썸네일(긴 변 480px) 주소
	 */
	@Schema(name = "ChatPhoto", description = "사진 메시지의 사진. 보관 기간(30일)이 지나도 사진 메시지인 것은 남는다(expired = true, 주소 null)")
	public record Photo(
			@Schema(description = "원본 너비(px). 화면이 사진이 뜨기 전에 말풍선 자리를 잡는다", example = "1600") int width,
			@Schema(description = "원본 높이(px)", example = "1200") int height,
			@Schema(description = "보관 기간(보낸 뒤 30일)이 지났는지. true면 url·thumbnailUrl이 null(「보관 기간이 지난 사진이에요」)",
					example = "false") boolean expired,
			@Schema(description = "원본(긴 변 1600px) 주소. 기간이 지났으면 null",
					example = "/api/chat-photos/3f2a9c1e5b7d4a608c1e2f3a4b5c6d7e.jpg", nullable = true) String url,
			@Schema(description = "썸네일(긴 변 480px) 주소. 기간이 지났으면 null",
					example = "/api/chat-photos/3f2a9c1e5b7d4a608c1e2f3a4b5c6d7e_t.jpg", nullable = true)
			String thumbnailUrl) {
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
