package com.ohjumwhat.poll;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.ranking.Adoption;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 투표 상세 화면 전체를 한 번에 그릴 수 있는 응답(폴링 대상).
 *
 * @param myOptionId myResponse가 OPTION일 때 내가 고른 메뉴
 * @param soloOptionIds 참여자가 한 명뿐인 메뉴
 * @param chatClosesAt 채팅이 닫히는 시각(마감 1시간 뒤). 그 뒤에는 읽기만 한다
 * @param adoption 마감된 투표의 채택 결과(메뉴 메이커 랭킹). 진행 중이면 null
 */
@Schema(description = "투표 상세 화면 전체(폴링 대상). 투표에 쓰는 API도 모두 이 모양으로 최신 상태를 준다")
public record PollDetailResponse(
		@Schema(description = "투표 ID", example = "10") Long id,
		@Schema(description = "조직 ID", example = "1") Long organizationId,
		@Schema(description = "제목", example = "오늘 점심") String title,
		PollStatus status,
		@Schema(description = "열린 시각(UTC). 한국 날짜가 투표 날짜다", example = "2026-10-12T01:00:00Z") Instant opensAt,
		@Schema(description = "마감 시각(UTC)", example = "2026-10-12T02:50:00Z") Instant closesAt,
		@Schema(description = "채팅이 닫히는 시각(마감 1시간 뒤, UTC). 그 뒤에는 읽기만 한다", example = "2026-10-12T03:50:00Z")
		Instant chatClosesAt,
		@Schema(description = "정기 투표로 열린 투표인지(삭제할 수 없다)", example = "false") boolean scheduled,
		@Schema(description = "조직 인원", example = "10") int memberCount,
		@Schema(description = "메뉴(먼저 올라온 순)") List<Option> options,
		MyResponse myResponse,
		@Schema(description = "myResponse가 OPTION일 때 내가 고른 메뉴 ID", example = "100", nullable = true) Long myOptionId,
		@Schema(description = "오늘은 패스한 사람") List<PersonResponse> passed,
		@Schema(description = "아직 응답하지 않은 멤버") List<PersonResponse> nonRespondents,
		@Schema(description = "참여자가 한 명뿐인 메뉴 ID", example = "[101]") List<Long> soloOptionIds,
		@Schema(description = "마감된 투표의 채택 결과. 진행 중이면 null", nullable = true) Adoption adoption) {

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
	@Schema(name = "PollOption", description = "투표의 메뉴 카드")
	public record Option(
			@Schema(description = "메뉴 ID", example = "100") Long id,
			@Schema(description = "메뉴 이름", example = "김치찌개") String name,
			@Schema(description = "식당 지도 링크(없으면 null)", example = "https://map.naver.com/p/entry/place/1234567", nullable = true)
			String link,
			@Schema(description = "식당 이름(링크가 있을 때만)", example = "할매집", nullable = true) String placeName,
			@Schema(description = "식당 주소(링크가 있을 때만). 지도 위치는 /api/orgs/{orgId}/places로 따로 받는다",
					example = "서울 강남구 테헤란로 123", nullable = true) String placeAddress,
			@Schema(description = "근처 식당 찾기로 고른 카카오 장소 ID. 이름·위치는 /api/orgs/{orgId}/places로 받는다",
					example = "12345678", nullable = true) String kakaoPlaceId,
			@Schema(description = "카카오 식당을 찾을 때 친 검색어", example = "김치찌개", nullable = true) String placeQuery,
			@Schema(description = "추가한 사람. 강제 탈퇴했으면 null(「탈퇴한 사용자」)", nullable = true) PersonResponse createdBy,
			@Schema(description = "이 메뉴에 참여한 사람") List<PersonResponse> voters,
			@Schema(description = "내가 추가한 메뉴인지", example = "true") boolean mine,
			@Schema(description = "지금 지울 수 있는지(내가 추가했고 참여자가 없고 진행 중)", example = "false") boolean deletable,
			@Schema(description = "댓글 수. 목록은 /api/polls/{pollId}/options/{optionId}/comments", example = "3") long commentCount) {
	}
}
