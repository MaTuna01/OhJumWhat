package com.ohjumwhat.ranking;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마감된 투표 하나의 채택 결과(메뉴 메이커 랭킹). 규칙은 {@link MenuAdoption}에 있다.
 *
 * @param optionId 채택된 메뉴. 메뉴를 고른 사람이 없거나 마감 당시 인원의 30%보다 적으면 null
 * @param participants 메뉴를 고른 사람 수(패스 제외)
 * @param headcount 마감 당시 인원: 마감 전에 들어온 지금 멤버 + 그 투표에 응답한 사람
 * @param required 채택에 필요한 최소 인원(마감 당시 인원의 30%, 올림)
 */
@Schema(description = "마감된 투표의 채택 결과(메뉴 메이커). 진행 중이면 응답에서 null")
public record Adoption(
		@Schema(description = "채택된 메뉴 ID. 고른 사람이 없거나 30% 미만이면 null", example = "100", nullable = true) Long optionId,
		@Schema(description = "메뉴를 고른 사람 수(패스 제외)", example = "6") int participants,
		@Schema(description = "마감 당시 인원", example = "10") int headcount,
		@Schema(description = "채택에 필요한 최소 인원(마감 당시 인원의 30%, 올림)", example = "3") int required) {
}
