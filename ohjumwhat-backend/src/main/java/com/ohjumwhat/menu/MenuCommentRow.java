package com.ohjumwhat.menu;

import java.time.Instant;

import com.ohjumwhat.user.User;

/** 댓글 조회 행. 쓴 사람이 강제 탈퇴로 지워졌으면 userId·name·profileImageUrl이 null이다. */
public record MenuCommentRow(Long id, Long userId, String name, String profileImageUrl, String body,
		Instant createdAt, Instant editedAt) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진(올린 사진, 없으면 구글 사진)을 정한다. */
	public MenuCommentRow(Long id, Long userId, String name, String photoKey, String googleUrl, String body,
			Instant createdAt, Instant editedAt) {
		this(id, userId, name, User.photoUrl(photoKey, googleUrl), body, createdAt, editedAt);
	}
}
