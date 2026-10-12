package com.ohjumwhat.ranking;

import java.time.LocalDate;
import java.util.List;

import com.ohjumwhat.poll.PersonResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 메뉴 메이커 랭킹(한 기간).
 *
 * @param from 기간 첫날(한국 날짜)
 * @param to 기간 마지막 날
 * @param hasPrevious 이전 기간도 볼 수 있는지(12개월 전까지)
 * @param hasNext 다음 기간이 이미 시작했는지
 * @param closedPollCount 기간 안에 마감된 투표 수
 * @param adoptedPollCount 그중 채택된 메뉴가 있는 투표 수
 * @param entries 1회 이상 채택된 지금 멤버(순위 순)
 * @param me 내 순위(이 기간에 채택이 없으면 rank는 null)
 */
@Schema(description = "메뉴 메이커 랭킹(한 기간)")
public record RankingResponse(
		RankingPeriod period,
		@Schema(description = "기간 첫날(한국 날짜)", example = "2026-10-12") LocalDate from,
		@Schema(description = "기간 마지막 날(한국 날짜, 포함)", example = "2026-10-18") LocalDate to,
		@Schema(description = "이전 기간도 볼 수 있는지(12개월 전까지, ‹ 버튼)", example = "true") boolean hasPrevious,
		@Schema(description = "다음 기간이 이미 시작했는지(› 버튼)", example = "false") boolean hasNext,
		@Schema(description = "기간 안에 마감된 투표 수", example = "5") int closedPollCount,
		@Schema(description = "그중 채택된 메뉴가 있는 투표 수", example = "4") int adoptedPollCount,
		@Schema(description = "1회 이상 채택된 지금 멤버(순위 순, 같은 횟수면 그 횟수에 먼저 도달한 사람 → 이름 순)")
		List<Entry> entries,
		Me me) {

	/**
	 * @param rank 순위. 같은 횟수는 같은 순위다(1, 1, 3)
	 * @param topMenu 대표 채택 메뉴(menus의 첫 번째)
	 * @param menus 채택된 메뉴를 이름별로 묶어 횟수 많은 순(같으면 최근 순)
	 */
	@Schema(name = "RankingEntry", description = "순위 한 줄(TOP3는 화면이 🥇 명예 셰프 · 🥈 기획왕 · 🥉 미식가 칭호를 붙인다)")
	public record Entry(
			@Schema(description = "순위. 같은 횟수는 같은 순위(1, 1, 3)", example = "1") int rank,
			PersonResponse user,
			@Schema(description = "채택 횟수", example = "3") int count,
			@Schema(description = "대표 채택 메뉴(menus의 첫 번째)", example = "김치찌개") String topMenu,
			@Schema(description = "채택된 메뉴를 이름별로 묶어 횟수 많은 순(같으면 최근 순). 채택 기록 모달(11-M)에 쓴다")
			List<Menu> menus) {
	}

	/**
	 * @param name 가장 최근에 채택된 이름(띄어쓰기·대소문자만 다른 이름은 묶는다)
	 * @param lastAdoptedOn 마지막으로 채택된 투표 날짜(한국)
	 */
	@Schema(name = "RankingMenu", description = "채택된 메뉴(이름별)")
	public record Menu(
			@Schema(description = "가장 최근에 채택된 이름(띄어쓰기·대소문자만 다른 이름은 묶는다)", example = "김치찌개") String name,
			@Schema(description = "채택 횟수", example = "2") int count,
			@Schema(description = "마지막으로 채택된 투표 날짜(한국)", example = "2026-10-12") LocalDate lastAdoptedOn) {
	}

	@Schema(name = "RankingMe", description = "내 순위(MyRankCard)")
	public record Me(
			@Schema(description = "내 순위. 이 기간에 채택이 없으면 null", example = "2", nullable = true) Integer rank,
			@Schema(description = "내 채택 횟수", example = "2") int count) {
	}
}
