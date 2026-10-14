package com.ohjumwhat.poll;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조직 홈의 투표 카드.
 *
 * @param chatClosesAt 채팅이 닫히는 시각(마감 1시간 뒤)
 * @param teamCount 참여자가 있는 메뉴 수(마감 후 나뉜 팀 수)
 * @param myOptionName myResponse가 OPTION일 때 내가 고른 메뉴 이름
 * @param unreadMessages 내가 안 읽은 채팅 메시지 수
 */
@Schema(description = "조직 홈의 투표 카드")
public record PollSummaryResponse(
		@Schema(description = "투표 ID", example = "10") Long id,
		@Schema(description = "제목", example = "오늘 점심") String title,
		PollStatus status,
		@Schema(description = "마감 시각(UTC)", example = "2026-10-12T02:50:00Z") Instant closesAt,
		@Schema(description = "채팅이 닫히는 시각(마감 1시간 뒤, UTC)", example = "2026-10-12T03:50:00Z") Instant chatClosesAt,
		@Schema(description = "조직 인원", example = "10") int memberCount,
		@Schema(description = "응답한 사람(참여 + 패스)", example = "7") int respondedCount,
		@Schema(description = "패스한 사람", example = "1") int passCount,
		@Schema(description = "메뉴 수", example = "4") int optionCount,
		@Schema(description = "참여자가 있는 메뉴 수(마감 후 나뉜 팀 수)", example = "3") int teamCount,
		MyResponse myResponse,
		@Schema(description = "myResponse가 OPTION일 때 내가 고른 메뉴 이름", example = "김치찌개", nullable = true)
		String myOptionName,
		@Schema(description = "내가 안 읽은 채팅 메시지 수", example = "2") int unreadMessages) {
}
