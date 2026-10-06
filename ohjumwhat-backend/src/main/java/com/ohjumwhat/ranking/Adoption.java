package com.ohjumwhat.ranking;

/**
 * 마감된 투표 하나의 채택 결과(메뉴 메이커 랭킹). 규칙은 {@link MenuAdoption}에 있다.
 *
 * @param optionId 채택된 메뉴. 메뉴를 고른 사람이 없거나 마감 당시 인원의 30%보다 적으면 null
 * @param participants 메뉴를 고른 사람 수(패스 제외)
 * @param headcount 마감 당시 인원: 마감 전에 들어온 지금 멤버 + 그 투표에 응답한 사람
 * @param required 채택에 필요한 최소 인원(마감 당시 인원의 30%, 올림)
 */
public record Adoption(Long optionId, int participants, int headcount, int required) {
}
