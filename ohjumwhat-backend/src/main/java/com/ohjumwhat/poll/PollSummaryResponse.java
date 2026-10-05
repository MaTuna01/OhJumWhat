package com.ohjumwhat.poll;

import java.time.Instant;

/**
 * 조직 홈의 투표 카드.
 *
 * @param chatClosesAt 채팅이 닫히는 시각(마감 1시간 뒤)
 * @param teamCount 참여자가 있는 메뉴 수(마감 후 나뉜 팀 수)
 * @param myOptionName myResponse가 OPTION일 때 내가 고른 메뉴 이름
 * @param unreadMessages 내가 안 읽은 채팅 메시지 수
 */
public record PollSummaryResponse(Long id, String title, PollStatus status, Instant closesAt, Instant chatClosesAt,
		int memberCount, int respondedCount, int passCount, int optionCount, int teamCount, MyResponse myResponse,
		String myOptionName, int unreadMessages) {
}
