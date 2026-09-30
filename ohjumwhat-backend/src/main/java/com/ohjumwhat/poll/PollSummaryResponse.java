package com.ohjumwhat.poll;

import java.time.Instant;

/**
 * 조직 홈의 투표 카드.
 *
 * @param teamCount 참여자가 있는 메뉴 수(마감 후 나뉜 팀 수)
 * @param myOptionName myResponse가 OPTION일 때 내가 고른 메뉴 이름
 */
public record PollSummaryResponse(Long id, String title, PollStatus status, Instant closesAt, int memberCount,
		int respondedCount, int passCount, int optionCount, int teamCount, MyResponse myResponse,
		String myOptionName) {
}
