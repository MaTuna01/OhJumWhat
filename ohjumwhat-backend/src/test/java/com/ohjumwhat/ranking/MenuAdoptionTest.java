package com.ohjumwhat.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.vote.Vote;

class MenuAdoptionTest {

	static final Instant CLOSES_AT = Instant.parse("2026-10-05T02:50:00Z");

	static final Instant BEFORE = CLOSES_AT.minusSeconds(86_400);

	/** 마감 전에 들어온 멤버 1~10번 */
	static final Map<Long, Instant> TEN_MEMBERS = Map.of(1L, BEFORE, 2L, BEFORE, 3L, BEFORE, 4L, BEFORE, 5L, BEFORE,
			6L, BEFORE, 7L, BEFORE, 8L, BEFORE, 9L, BEFORE, 10L, BEFORE);

	@Test
	void 메뉴를_고른_사람이_가장_많은_메뉴를_채택한다() {
		Adoption adoption = MenuAdoption.decide(List.of(vote(1, 20L), vote(2, 21L), vote(3, 21L), vote(4, null)),
				TEN_MEMBERS, CLOSES_AT);

		assertThat(adoption).isEqualTo(new Adoption(21L, 3, 10, 3));
	}

	@Test
	void 같으면_먼저_추가한_메뉴를_채택한다() {
		Adoption adoption = MenuAdoption.decide(
				List.of(vote(1, 22L), vote(2, 21L), vote(3, 22L), vote(4, 21L), vote(5, 30L)), TEN_MEMBERS,
				CLOSES_AT);

		assertThat(adoption.optionId()).isEqualTo(21L);
	}

	@Test
	void 메뉴를_고른_사람이_마감_당시_인원의_30퍼센트보다_적으면_채택하지_않는다() {
		// 10명의 30% = 3명. 패스는 인원에는 들어가지만 메뉴를 고른 사람에는 들어가지 않는다.
		Adoption twoOfTen = MenuAdoption.decide(List.of(vote(1, 20L), vote(2, 20L), vote(3, null)), TEN_MEMBERS,
				CLOSES_AT);
		assertThat(twoOfTen).isEqualTo(new Adoption(null, 2, 10, 3));

		// 12명이면 3.6명을 올려 4명이다.
		Map<Long, Instant> twelve = new HashMap<>(TEN_MEMBERS);
		twelve.put(11L, BEFORE);
		twelve.put(12L, BEFORE);
		Adoption threeOfTwelve = MenuAdoption.decide(List.of(vote(1, 20L), vote(2, 20L), vote(3, 21L)), twelve,
				CLOSES_AT);
		assertThat(threeOfTwelve).isEqualTo(new Adoption(null, 3, 12, 4));
	}

	@Test
	void 마감_뒤에_들어온_사람은_빼고_떠난_응답자는_센다() {
		Map<Long, Instant> members = Map.of(1L, BEFORE, 2L, CLOSES_AT, 3L, CLOSES_AT.plusSeconds(1));
		// 1·2번은 마감 때 멤버(가입 시각이 마감과 같아도 멤버), 3번은 마감 뒤에 들어왔고, 9번은 응답한 뒤 떠났다.
		Adoption adoption = MenuAdoption.decide(List.of(vote(9, 20L)), members, CLOSES_AT);

		assertThat(adoption).isEqualTo(new Adoption(20L, 1, 3, 1));
	}

	@Test
	void 아무도_메뉴를_고르지_않으면_채택이_없다() {
		assertThat(MenuAdoption.decide(List.of(), TEN_MEMBERS, CLOSES_AT)).isEqualTo(new Adoption(null, 0, 10, 3));
		assertThat(MenuAdoption.decide(List.of(vote(1, null)), Map.of(), CLOSES_AT))
			.isEqualTo(new Adoption(null, 0, 1, 1));
		assertThat(MenuAdoption.decide(List.of(), Map.of(), CLOSES_AT)).isEqualTo(new Adoption(null, 0, 0, 0));
	}

	private static Vote vote(long userId, Long optionId) {
		return new Vote(1L, userId, optionId);
	}
}
