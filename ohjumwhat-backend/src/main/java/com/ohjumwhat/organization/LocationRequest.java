package com.ohjumwhat.organization;

import jakarta.validation.constraints.Size;

import com.ohjumwhat.common.ApiLimit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조직 위치(통째로 바꾼다). 빈 값은 지운다.
 *
 * @param area 검색 지역(예: 역삼동). 20자까지
 * @param officeLink 장소 지도 링크. 지도 앱의 공유 문구를 통째로 붙여도 된다
 * @param officeName 장소 이름(링크가 있을 때만 저장). 공백을 정리한 뒤 100자까지
 * @param officeAddress 조직 주소. 근처 식당 검색·지도의 기준점. 공백을 정리한 뒤 200자까지
 * @param searchRadius 근처 식당 검색 반경(m): 500, 1000, 2000. 없으면 1000
 */
@Schema(description = "조직 위치(통째로 바꾼다). 빈 값은 지운다")
public record LocationRequest(
		@Schema(description = "검색 지역(20자). 「네이버 지도에서 찾기」 검색어 앞에 붙는다", example = "역삼동", nullable = true)
		@Size(max = 20, message = "검색 지역은 20자 이하로 입력해 주세요.")
		String area,
		@Schema(description = "장소 지도 링크. 지도 앱의 공유 문구를 통째로 붙여도 된다(원문 1000자, 꺼내 정리한 링크 500자)", example = "https://naver.me/abcd1234",
				nullable = true)
		@Size(max = 1000, message = "링크가 너무 길어요.")
		String officeLink,
		@Schema(description = "장소 이름(officeLink가 있을 때만 저장, 공백 정리 후 100자)", example = "오점왓 타워", nullable = true)
		@ApiLimit(100)
		@Size(max = 200, message = "장소 이름은 100자 이하로 입력해 주세요.")
		String officeName,
		@Schema(description = "조직 주소(공백 정리 후 200자). 근처 식당 찾기·지도·거리의 기준점이라 카카오로 찾을 수 있어야 한다",
				example = "서울 강남구 테헤란로 123", nullable = true)
		@ApiLimit(200)
		@Size(max = 400, message = "주소는 200자 이하로 입력해 주세요.")
		String officeAddress,
		@Schema(description = "근처 식당 찾기 반경(m): 500·1000·2000. 없으면 1000", example = "1000",
				allowableValues = { "500", "1000", "2000" }, nullable = true)
		Integer searchRadius) {
}
