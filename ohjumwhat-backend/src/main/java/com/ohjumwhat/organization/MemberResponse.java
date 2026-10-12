package com.ohjumwhat.organization;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.user.PersonalColor;
import com.ohjumwhat.user.ProfileDetailsResponse;
import com.ohjumwhat.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * name은 별명(없으면 구글 이름), profileImageUrl은 올린 사진(없으면 구글 사진).
 * bio(없으면 null)·foodTags(없으면 빈 목록)는 프로필 소개, details(채우지 않았으면 null)는 상세 프로필로,
 * 같은 조직 멤버가 프로필 모달에서 본다.
 */
@Schema(description = "조직 멤버(멤버 목록·멤버 프로필 모달)")
public record MemberResponse(
		@Schema(description = "회원 ID", example = "7") Long userId,
		@Schema(description = "별명, 없으면 구글 이름", example = "김오점") String name,
		@Schema(description = "올린 사진(/api/photos/{key}.jpg), 없으면 구글 사진. 둘 다 없으면 null",
				example = "/api/photos/3f2a9c1e5b7d4f60a1b2c3d4e5f60718.jpg", nullable = true) String profileImageUrl,
		@Schema(description = "한줄 소개. 없으면 null", example = "점심은 무조건 국밥", nullable = true) String bio,
		@Schema(description = "좋아하는 음식(적은 순서, 최대 3개). 없으면 빈 목록", example = "[\"국밥\", \"떡볶이\"]")
		List<String> foodTags,
		@Schema(description = "상세 프로필. 채우지 않았으면 null", nullable = true) ProfileDetailsResponse details,
		@Schema(description = "조직에 들어온 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant joinedAt) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진을 정하고, 배열을 목록으로·상세 프로필 컬럼을 묶음으로 바꾼다. */
	public MemberResponse(Long userId, String name, String photoKey, String googleUrl, String bio,
			String[] foodTags, String mbti, PersonalColor personalColor, String[] hobbies, Short age, String jobTitle,
			Instant joinedAt) {
		this(userId, name, User.photoUrl(photoKey, googleUrl), bio, User.foodTags(foodTags),
				ProfileDetailsResponse.of(mbti, personalColor, hobbies, age, jobTitle), joinedAt);
	}
}
