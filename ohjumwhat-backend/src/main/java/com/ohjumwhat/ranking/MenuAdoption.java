package com.ohjumwhat.ranking;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ohjumwhat.vote.Vote;

/**
 * 채택 규칙(Notion 「15. 메뉴 채택 랭킹 기능 추가」). 랭킹과 투표 결과 화면이 모두 이 함수를 쓴다.
 * <ul>
 * <li>메뉴를 고른 사람(패스 제외)이 가장 많은 메뉴 하나를 채택한다. 같으면 먼저 추가한 메뉴(id가 작은 쪽)다.
 * 결과 화면의 확정 팀 순서(PollService.historyItem, 프론트 confirmedTeams)와 같다.</li>
 * <li>메뉴를 고른 사람이 마감 당시 인원의 30%(올림)보다 적으면 채택하지 않는다.</li>
 * <li>마감 당시 인원은 「마감 전에 들어온 지금 멤버」와 「그 투표에 응답한 사람」의 합집합이다. 조직을 떠나면 멤버 행이
 * 지워져 과거 인원을 그대로 알 수 없어서, 스키마를 바꾸지 않고 이렇게 추정한다. 마감 뒤에 들어온 사람은 빠지므로 조직이
 * 커져도 지난 기록이 바뀌지 않는다.</li>
 * </ul>
 */
public final class MenuAdoption {

	/** 메뉴를 고른 사람이 마감 당시 인원의 이 비율(%) 이상이어야 채택한다. */
	static final int MIN_PERCENT = 30;

	private MenuAdoption() {
	}

	/**
	 * @param votes 그 투표의 응답(패스 포함)
	 * @param memberJoinedAt 지금 조직 멤버의 가입 시각(userId → joinedAt)
	 * @param closesAt 투표 마감 시각
	 */
	public static Adoption decide(Collection<Vote> votes, Map<Long, Instant> memberJoinedAt, Instant closesAt) {
		Set<Long> headcount = new HashSet<>();
		memberJoinedAt.forEach((userId, joinedAt) -> {
			if (!joinedAt.isAfter(closesAt)) {
				headcount.add(userId);
			}
		});
		Map<Long, Integer> countByOption = new HashMap<>();
		int participants = 0;
		for (Vote vote : votes) {
			headcount.add(vote.getUserId());
			if (!vote.isPass()) {
				participants++;
				countByOption.merge(vote.getOptionId(), 1, Integer::sum);
			}
		}

		Long top = null;
		int topCount = 0;
		for (Map.Entry<Long, Integer> entry : countByOption.entrySet()) {
			int count = entry.getValue();
			if (count > topCount || (count == topCount && entry.getKey() < top)) {
				top = entry.getKey();
				topCount = count;
			}
		}
		int required = (headcount.size() * MIN_PERCENT + 99) / 100;
		boolean enough = participants > 0 && participants >= required;
		return new Adoption(enough ? top : null, participants, headcount.size(), required);
	}
}
