package com.ohjumwhat.guestbook;

import java.time.Instant;

import com.ohjumwhat.user.User;

/**
 * 방명록 목록 행. author*는 쓴 사람(강제 탈퇴했으면 null)이고, 이름은 별명(없으면 구글 이름)이다.
 * 제한된 글은 쿼리가 본문을 아예 고르지 않아 body가 null이다.
 */
record GuestbookEntryRow(Long id, Long authorId, String authorName, String authorPhotoUrl, String body,
		Instant createdAt, boolean restricted) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진(올린 사진, 없으면 구글 사진)을 정한다. */
	GuestbookEntryRow(Long id, Long authorId, String authorName, String photoKey, String googleUrl, String body,
			Instant createdAt, boolean restricted) {
		this(id, authorId, authorName, authorId == null ? null : User.photoUrl(photoKey, googleUrl), body, createdAt,
				restricted);
	}
}
