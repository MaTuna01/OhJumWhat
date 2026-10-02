package com.ohjumwhat.organization;

/**
 * @param area 검색 지역(예: 역삼동). 없으면 null
 * @param officeName 장소 이름. 없으면 null
 * @param officeLink 장소 지도 링크. 없으면 null
 * @param officeAddress 조직 주소(근처 식당 검색·지도의 기준점). 없으면 null
 * @param searchRadius 근처 식당 검색 반경(m)
 */
public record OrganizationResponse(Long id, String name, String inviteToken, long memberCount, String area,
		String officeName, String officeLink, String officeAddress, int searchRadius) {
}
