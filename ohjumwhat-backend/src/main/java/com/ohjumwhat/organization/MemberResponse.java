package com.ohjumwhat.organization;

import java.time.Instant;

import com.ohjumwhat.user.User;

/** name은 별명(없으면 구글 이름), profileImageUrl은 올린 사진(없으면 구글 사진) */
public record MemberResponse(Long userId, String name, String profileImageUrl, Instant joinedAt) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정한다. */
	public MemberResponse(Long userId, String name, String photoKey, String googleUrl, Instant joinedAt) {
		this(userId, name, User.photoUrl(photoKey, googleUrl), joinedAt);
	}
}
