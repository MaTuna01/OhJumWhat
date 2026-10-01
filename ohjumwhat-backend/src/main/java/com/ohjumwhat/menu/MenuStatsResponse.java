package com.ohjumwhat.menu;

import java.time.LocalDate;
import java.util.List;

/**
 * 조직 통계 화면.
 *
 * @param days 최근 며칠인지(null이면 전체 기간)
 * @param from 집계 시작 날짜(null이면 전체 기간)
 * @param pollCount 기간 안에 마감된 투표 수
 * @param menus 먹은 메뉴(많이 먹은 순, 최대 30개)
 */
public record MenuStatsResponse(Integer days, LocalDate from, int pollCount, List<MenuStat> menus) {

	/**
	 * @param times 먹은 투표 수
	 * @param people 참여 인원 합계(연인원)
	 * @param lastEatenOn 마지막으로 먹은 투표 날짜(한국)
	 */
	public record MenuStat(String name, int times, int people, LocalDate lastEatenOn) {
	}
}
