package com.ohjumwhat.chat;

import java.time.Instant;

import com.ohjumwhat.user.User;

/**
 * 채팅 조회 행. 쓴 사람이 강제 탈퇴로 지워졌으면 userId·name·profileImageUrl이 null이다.
 * 사진 메시지면 imageKey·imageWidth·imageHeight가 있다(글 메시지·지운 메시지는 null).
 */
public record ChatMessageRow(Long id, Long userId, String name, String profileImageUrl, String body, String imageKey,
		Integer imageWidth, Integer imageHeight, Instant createdAt, Instant editedAt, Instant deletedAt) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진(올린 사진, 없으면 구글 사진)을 정한다. */
	public ChatMessageRow(Long id, Long userId, String name, String photoKey, String googleUrl, String body,
			String imageKey, Integer imageWidth, Integer imageHeight, Instant createdAt, Instant editedAt,
			Instant deletedAt) {
		this(id, userId, name, User.photoUrl(photoKey, googleUrl), body, imageKey, imageWidth, imageHeight, createdAt,
				editedAt, deletedAt);
	}
}
