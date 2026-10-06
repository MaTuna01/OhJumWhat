package com.ohjumwhat.ranking;

import java.time.LocalDate;
import java.util.List;

import com.ohjumwhat.poll.PersonResponse;

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
public record RankingResponse(RankingPeriod period, LocalDate from, LocalDate to, boolean hasPrevious,
		boolean hasNext, int closedPollCount, int adoptedPollCount, List<Entry> entries, Me me) {

	/**
	 * @param rank 순위. 같은 횟수는 같은 순위다(1, 1, 3)
	 * @param topMenu 대표 채택 메뉴(menus의 첫 번째)
	 * @param menus 채택된 메뉴를 이름별로 묶어 횟수 많은 순(같으면 최근 순)
	 */
	public record Entry(int rank, PersonResponse user, int count, String topMenu, List<Menu> menus) {
	}

	/**
	 * @param name 가장 최근에 채택된 이름(띄어쓰기·대소문자만 다른 이름은 묶는다)
	 * @param lastAdoptedOn 마지막으로 채택된 투표 날짜(한국)
	 */
	public record Menu(String name, int count, LocalDate lastAdoptedOn) {
	}

	public record Me(Integer rank, int count) {
	}
}
