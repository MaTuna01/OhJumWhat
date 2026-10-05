package com.ohjumwhat.letter;

import java.time.Instant;

import com.ohjumwhat.user.User;

/** 차단 목록 행. 익명 차단은 쿼리가 차단한 사람을 고르지 않아 userId·name·photoUrl이 null이다. */
public record LetterBlockRow(Long id, boolean anonymous, Long userId, String name, String photoUrl, String letterBody,
		Instant createdAt) {

	/** JPQL용 */
	public LetterBlockRow(Long id, boolean anonymous, Long userId, String name, String photoKey, String googleUrl,
			String letterBody, Instant createdAt) {
		this(id, anonymous, userId, name, userId == null ? null : User.photoUrl(photoKey, googleUrl), letterBody,
				createdAt);
	}
}
