package com.ohjumwhat.menu;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조직 통계 화면.
 *
 * @param days 최근 며칠인지(null이면 전체 기간)
 * @param from 집계 시작 날짜(null이면 전체 기간)
 * @param pollCount 기간 안에 마감된 투표 수
 * @param menus 먹은 메뉴(많이 먹은 순, 최대 30개)
 */
@Schema(description = "조직 통계(D08). 먹은 메뉴 = 마감된 투표에서 참여자가 한 명 이상인 메뉴. "
		+ "이름은 소문자·띄어쓰기 제거로 묶는다(「김치찌개」 = 「김치 찌개」)")
public record MenuStatsResponse(
		@Schema(description = "최근 며칠인지(요청의 days). 전체 기간이면 null", example = "30", nullable = true) Integer days,
		@Schema(description = "집계 시작 날짜(한국, 오늘 포함 days일). 전체 기간이면 null", example = "2026-09-13",
				nullable = true) LocalDate from,
		@Schema(description = "기간 안에 마감된 투표 수", example = "18") int pollCount,
		@Schema(description = "먹은 메뉴(많이 먹은 순, 최대 30개)") List<MenuStat> menus) {

	/**
	 * @param times 먹은 투표 수
	 * @param people 참여 인원 합계(연인원)
	 * @param lastEatenOn 마지막으로 먹은 투표 날짜(한국)
	 */
	@Schema(description = "먹은 메뉴 한 줄")
	public record MenuStat(
			@Schema(description = "메뉴 이름(같이 묶인 이름 중 가장 최근 이름)", example = "김치찌개") String name,
			@Schema(description = "먹은 투표 수", example = "5") int times,
			@Schema(description = "참여 인원 합계(연인원)", example = "17") int people,
			@Schema(description = "마지막으로 먹은 투표 날짜(한국)", example = "2026-10-08") LocalDate lastEatenOn) {
	}

	/**
	 * 「오늘은 이거 어때요?」 추천 항목.
	 *
	 * @param lastPlace 같은 이름의 메뉴에 지난번 붙인 식당. 없으면 null
	 */
	@Schema(name = "MenuRecommendation", description = "「오늘은 이거 어때요?」 추천 항목")
	public record Recommendation(
			@Schema(description = "메뉴 이름(같이 묶인 이름 중 가장 최근 이름)", example = "제육볶음") String name,
			@Schema(description = "먹은 투표 수", example = "4") int times,
			@Schema(description = "참여 인원 합계(연인원)", example = "12") int people,
			@Schema(description = "마지막으로 먹은 투표 날짜(한국, 최근 7일보다 앞)", example = "2026-09-30")
			LocalDate lastEatenOn,
			@Schema(description = "같은 이름의 메뉴에 지난번 붙인 식당. 없으면 null", nullable = true) LastPlace lastPlace) {
	}
}
