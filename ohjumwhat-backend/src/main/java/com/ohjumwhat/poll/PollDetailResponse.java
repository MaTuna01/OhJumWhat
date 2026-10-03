package com.ohjumwhat.poll;

import java.time.Instant;
import java.util.List;

/**
 * 투표 상세 화면 전체를 한 번에 그릴 수 있는 응답(폴링 대상).
 *
 * @param myOptionId myResponse가 OPTION일 때 내가 고른 메뉴
 * @param soloOptionIds 참여자가 한 명뿐인 메뉴
 * @param chatClosesAt 채팅이 닫히는 시각(마감 1시간 뒤). 그 뒤에는 읽기만 한다
 */
public record PollDetailResponse(Long id, Long organizationId, String title, PollStatus status, Instant opensAt,
		Instant closesAt, Instant chatClosesAt, boolean scheduled, int memberCount, List<Option> options, MyResponse myResponse,
		Long myOptionId, List<PersonResponse> passed, List<PersonResponse> nonRespondents, List<Long> soloOptionIds) {

	/**
	 * @param link 식당 지도 링크(없으면 null)
	 * @param placeName 식당 이름(없으면 null). 링크가 있을 때만 있다
	 * @param placeAddress 식당 주소(없으면 null). 링크가 있을 때만 있다. 지도 위치는 /api/orgs/{id}/places로 따로 받는다
	 * @param kakaoPlaceId 근처 식당 찾기로 고른 카카오 장소 ID(없으면 null). 이름·위치는 /api/orgs/{id}/places로 받는다
	 * @param placeQuery 카카오 식당을 찾을 때 친 검색어(없으면 null)
	 * @param mine 내가 추가한 메뉴인지
	 * @param deletable 내가 추가했고 참여자가 없고 투표가 진행 중이라 삭제할 수 있는지
	 * @param commentCount 댓글 수. 목록은 /api/polls/{pollId}/options/{optionId}/comments로 따로 받는다
	 */
	public record Option(Long id, String name, String link, String placeName, String placeAddress,
			String kakaoPlaceId, String placeQuery, PersonResponse createdBy, List<PersonResponse> voters,
			boolean mine, boolean deletable, long commentCount) {
	}
}
