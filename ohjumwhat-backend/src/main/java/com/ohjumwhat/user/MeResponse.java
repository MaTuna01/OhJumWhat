package com.ohjumwhat.user;

import java.util.List;

import com.ohjumwhat.sanction.ActiveSanction;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param name 화면에 보여줄 이름(별명, 없으면 구글 이름)
 * @param nickname 마이페이지에서 정한 별명. 없으면 null
 * @param googleName 구글 계정 이름
 * @param profileImageUrl 화면에 보여줄 사진(올린 사진, 없으면 구글 사진). 둘 다 없으면 null
 * @param googleProfileImageUrl 구글 프로필 사진. 「구글 사진으로 되돌리기」 미리보기에 쓴다
 * @param customPhoto 직접 올린 사진이 있으면 true
 * @param bio 한줄 소개. 없으면 null
 * @param foodTags 좋아하는 음식(적은 순서, 최대 3개). 없으면 빈 목록
 * @param details 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급). 채우지 않았으면 null
 * @param lastVisitedOrgId 로그인 후 이동할 최근 조직. 속한 조직이 없으면 null
 * @param admin 관리자면 프로필 메뉴에 관리자 콘솔이 보인다
 * @param sanctions 지금 걸려 있는 제재(오래된 순, 없으면 빈 목록). 화면이 막힌 기능을 끄고 안내한다
 */
@Schema(description = "내 정보(마이페이지·상단 바). 프로필을 바꾸는 API도 모두 이 모양으로 바뀐 정보를 준다")
public record MeResponse(
		@Schema(description = "회원 ID", example = "7") Long id,
		@Schema(description = "화면에 보여줄 이름: 별명, 없으면 구글 이름", example = "김오점") String name,
		@Schema(description = "마이페이지에서 정한 별명. 없으면 null", example = "김오점", nullable = true) String nickname,
		@Schema(description = "구글 계정 이름(로그인 때마다 갱신)", example = "김철수") String googleName,
		@Schema(description = "구글 계정 이메일", example = "kim@example.com") String email,
		@Schema(description = "화면에 보여줄 사진: 올린 사진(/api/photos/{key}.jpg), 없으면 구글 사진. 둘 다 없으면 null",
				example = "/api/photos/3f2a9c1e5b7d4f60a1b2c3d4e5f60718.jpg", nullable = true) String profileImageUrl,
		@Schema(description = "구글 프로필 사진. 「구글 사진으로 되돌리기」 미리보기에 쓴다. 없으면 null",
				example = "https://lh3.googleusercontent.com/a/abc123", nullable = true) String googleProfileImageUrl,
		@Schema(description = "직접 올린 사진이 있는지", example = "true") boolean customPhoto,
		@Schema(description = "한줄 소개. 없으면 null", example = "점심은 무조건 국밥", nullable = true) String bio,
		@Schema(description = "좋아하는 음식(적은 순서, 최대 3개). 없으면 빈 목록", example = "[\"국밥\", \"떡볶이\"]")
		List<String> foodTags,
		@Schema(description = "상세 프로필. 채우지 않았으면 null(화면이 채우기 안내 03-N4를 보여준다)", nullable = true)
		ProfileDetailsResponse details,
		@Schema(description = "로그인 후 이동할 최근 조직 ID. 속한 조직이 없으면 null", example = "1", nullable = true)
		Long lastVisitedOrgId,
		@Schema(description = "관리자인지(프로필 메뉴에 관리자 콘솔이 보인다)", example = "false") boolean admin,
		@Schema(description = "지금 걸려 있는 제재(오래된 순, 없으면 빈 목록). 화면이 막힌 기능을 끄고 안내한다")
		List<ActiveSanction> sanctions) {
}
