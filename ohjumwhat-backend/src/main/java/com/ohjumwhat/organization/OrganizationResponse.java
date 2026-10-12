package com.ohjumwhat.organization;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param area 검색 지역(예: 역삼동). 없으면 null
 * @param officeName 장소 이름. 없으면 null
 * @param officeLink 장소 지도 링크. 없으면 null
 * @param officeAddress 조직 주소(근처 식당 검색·지도의 기준점). 없으면 null
 * @param searchRadius 근처 식당 검색 반경(m)
 */
@Schema(description = "조직 정보(조직 홈·조직 설정)")
public record OrganizationResponse(
		@Schema(description = "조직 ID", example = "1") Long id,
		@Schema(description = "조직 이름", example = "개발팀") String name,
		@Schema(description = "초대 링크 토큰(43자). 초대 링크는 /invite/{inviteToken}", example = "q3Zt8sKpX1vYb2Lm9NcRw4JhFe6UaDiG0oTyQ7lVnMk")
		String inviteToken,
		@Schema(description = "지금 인원", example = "10") long memberCount,
		@Schema(description = "검색 지역. 없으면 null", example = "역삼동", nullable = true) String area,
		@Schema(description = "장소 이름. 없으면 null", example = "오점왓 타워", nullable = true) String officeName,
		@Schema(description = "장소 지도 링크. 없으면 null", example = "https://map.naver.com/p/entry/place/1234567", nullable = true)
		String officeLink,
		@Schema(description = "조직 주소(근처 식당 찾기·지도·거리의 기준점). 없으면 null", example = "서울 강남구 테헤란로 123",
				nullable = true) String officeAddress,
		@Schema(description = "근처 식당 찾기 반경(m): 500·1000·2000", example = "1000") int searchRadius) {
}
