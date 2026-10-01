package com.ohjumwhat.poll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 조직 홈의 「지난 투표」 한 페이지(최신순).
 *
 * @param hasMore 다음 페이지가 있는지(「더 보기」)
 */
public record PollHistoryResponse(List<Item> polls, boolean hasMore) {

	/**
	 * @param teams 참여자가 있는 메뉴(확정 팀). 인원 많은 순, 같으면 먼저 올라온 순
	 * @param myOptionName myResponse가 OPTION일 때 내가 고른 메뉴 이름
	 */
	public record Item(Long id, String title, LocalDate pollDate, Instant closesAt, int respondedCount,
			int passCount, List<Team> teams, MyResponse myResponse, String myOptionName) {
	}

	public record Team(String name, int count) {
	}
}
