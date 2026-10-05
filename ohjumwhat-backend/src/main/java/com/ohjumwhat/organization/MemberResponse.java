package com.ohjumwhat.organization;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.user.User;

/**
 * name은 별명(없으면 구글 이름), profileImageUrl은 올린 사진(없으면 구글 사진).
 * bio(없으면 null)·foodTags(없으면 빈 목록)는 프로필 소개로, 같은 조직 멤버가 프로필 모달에서 본다.
 */
public record MemberResponse(Long userId, String name, String profileImageUrl, String bio, List<String> foodTags,
		Instant joinedAt) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정하고, 음식 배열을 목록으로 바꾼다. */
	public MemberResponse(Long userId, String name, String photoKey, String googleUrl, String bio,
			String[] foodTags, Instant joinedAt) {
		this(userId, name, User.photoUrl(photoKey, googleUrl), bio, User.foodTags(foodTags), joinedAt);
	}
}
