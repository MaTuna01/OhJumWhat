package com.ohjumwhat.user;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 상세 프로필. 채우지 않은 회원은 응답에서 이 값 대신 null이다.
 * @param mbti 예: ENFP
 * @param personalColor 퍼스널컬러
 * @param hobbies 취미(적은 순서, 1~5개)
 * @param age 나이(1~120)
 * @param jobTitle 직급(15자)
 */
@Schema(description = "상세 프로필(다섯 항목이 함께 채워져 있다). 채우지 않은 회원은 이 값 대신 null이다")
public record ProfileDetailsResponse(
		@Schema(description = "MBTI(대문자 4글자)", example = "ENFP") String mbti,
		PersonalColor personalColor,
		@Schema(description = "취미(적은 순서, 1~5개)", example = "[\"등산\", \"보드게임\"]") List<String> hobbies,
		@Schema(description = "나이(1~120)", example = "29") int age,
		@Schema(description = "직급(15자)", example = "대리") String jobTitle) {

	/** 채우지 않았으면 null(DB CHECK로 다섯 항목이 함께 비어 있거나 함께 채워져 있다). JPQL로 고른 DTO도 이것으로 만든다. */
	public static ProfileDetailsResponse of(String mbti, PersonalColor personalColor, String[] hobbies, Short age,
			String jobTitle) {
		if (mbti == null) {
			return null;
		}
		return new ProfileDetailsResponse(mbti, personalColor, User.tagList(hobbies), age, jobTitle);
	}
}
