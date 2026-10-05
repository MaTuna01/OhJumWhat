package com.ohjumwhat.organization;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.user.PersonalColor;
import com.ohjumwhat.user.ProfileDetailsResponse;
import com.ohjumwhat.user.User;

/**
 * name은 별명(없으면 구글 이름), profileImageUrl은 올린 사진(없으면 구글 사진).
 * bio(없으면 null)·foodTags(없으면 빈 목록)는 프로필 소개, details(채우지 않았으면 null)는 상세 프로필로,
 * 같은 조직 멤버가 프로필 모달에서 본다.
 */
public record MemberResponse(Long userId, String name, String profileImageUrl, String bio, List<String> foodTags,
		ProfileDetailsResponse details, Instant joinedAt) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정하고, 배열을 목록으로·상세 프로필 컬럼을 묶음으로 바꾼다. */
	public MemberResponse(Long userId, String name, String photoKey, String googleUrl, String bio,
			String[] foodTags, String mbti, PersonalColor personalColor, String[] hobbies, Short age, String jobTitle,
			Instant joinedAt) {
		this(userId, name, User.photoUrl(photoKey, googleUrl), bio, User.foodTags(foodTags),
				ProfileDetailsResponse.of(mbti, personalColor, hobbies, age, jobTitle), joinedAt);
	}
}
