package com.ohjumwhat.organization;

import jakarta.validation.constraints.Size;

/**
 * 조직 위치(통째로 바꾼다). 빈 값은 지운다.
 *
 * @param area 검색 지역(예: 역삼동). 20자까지
 * @param officeLink 회사 위치 지도 링크. 지도 앱의 공유 문구를 통째로 붙여도 된다
 * @param officeName 회사 위치 이름(링크가 있을 때만 저장). 공백을 정리한 뒤 100자까지
 * @param officeAddress 회사 주소. 근처 식당 검색·지도의 기준점. 공백을 정리한 뒤 200자까지
 * @param searchRadius 근처 식당 검색 반경(m): 500, 1000, 2000. 없으면 1000
 */
public record LocationRequest(
		@Size(max = 20, message = "검색 지역은 20자 이하로 입력해 주세요.")
		String area,
		@Size(max = 1000, message = "링크가 너무 길어요.")
		String officeLink,
		@Size(max = 200, message = "회사 이름은 100자 이하로 입력해 주세요.")
		String officeName,
		@Size(max = 400, message = "주소는 200자 이하로 입력해 주세요.")
		String officeAddress,
		Integer searchRadius) {
}
