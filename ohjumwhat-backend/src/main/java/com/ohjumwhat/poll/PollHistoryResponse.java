package com.ohjumwhat.poll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조직 홈의 「지난 투표」 한 페이지(최신순).
 *
 * @param hasMore 다음 페이지가 있는지(「더 보기」)
 */
@Schema(description = "「지난 투표」 한 페이지(최신순 10개)")
public record PollHistoryResponse(@Schema(description = "지난 투표(최신순)") List<Item> polls,
		@Schema(description = "다음 페이지가 있는지(「더 보기」)", example = "true") boolean hasMore) {

	/**
	 * @param teams 참여자가 있는 메뉴(확정 팀). 인원 많은 순, 같으면 먼저 올라온 순
	 * @param myOptionName myResponse가 OPTION일 때 내가 고른 메뉴 이름
	 */
	@Schema(name = "PollHistoryItem")
	public record Item(
			@Schema(description = "투표 ID", example = "9") Long id,
			@Schema(description = "제목", example = "어제 점심") String title,
			@Schema(description = "투표 날짜(한국)", example = "2026-10-11") LocalDate pollDate,
			@Schema(description = "마감 시각(UTC)", example = "2026-10-11T02:50:00Z") Instant closesAt,
			@Schema(description = "응답한 사람(참여 + 패스)", example = "8") int respondedCount,
			@Schema(description = "패스한 사람", example = "1") int passCount,
			@Schema(description = "참여자가 있는 메뉴(확정 팀). 인원 많은 순, 같으면 먼저 올라온 순") List<Team> teams,
			MyResponse myResponse,
			@Schema(description = "내가 고른 메뉴 이름", example = "김치찌개", nullable = true) String myOptionName) {
	}

	@Schema(name = "PollHistoryTeam")
	public record Team(@Schema(description = "메뉴 이름", example = "김치찌개") String name,
			@Schema(description = "참여 인원", example = "4") int count) {
	}
}
