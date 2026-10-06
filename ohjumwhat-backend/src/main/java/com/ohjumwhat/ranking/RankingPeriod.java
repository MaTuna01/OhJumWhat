package com.ohjumwhat.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

import com.ohjumwhat.common.ApiException;

/**
 * 랭킹 기간. 투표 날짜(poll_date, 한국 날짜) 기준으로 주간은 월~일, 월간은 1일~말일이다.
 * 화면의 lib/ranking.ts와 같은 규칙이라 함께 고친다.
 */
public enum RankingPeriod {

	WEEK, MONTH;

	/** 지난 기록은 이번 달 1일의 12개월 전부터 볼 수 있다(기록을 지우지 않고 조회 범위만 막는다). */
	static final int HISTORY_MONTHS = 12;

	/** 요청 값 week·month(대소문자 무시). 비면 주간 */
	static RankingPeriod parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return WEEK;
		}
		try {
			return valueOf(raw.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("기간은 week나 month예요.");
		}
	}

	/** 이 날짜가 들어 있는 기간 */
	Range rangeOf(LocalDate date) {
		return switch (this) {
			case WEEK -> {
				LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				yield new Range(monday, monday.plusDays(6));
			}
			case MONTH -> new Range(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
		};
	}

	/** 볼 수 있는 가장 이른 날짜. 이 날짜가 들어 있는 기간까지 볼 수 있다. */
	static LocalDate earliest(LocalDate today) {
		return today.withDayOfMonth(1).minusMonths(HISTORY_MONTHS);
	}

	/** 기간의 첫날과 마지막 날(둘 다 포함) */
	record Range(LocalDate from, LocalDate to) {

		/** 이전 기간(from 하루 전이 들어 있는 기간)도 볼 수 있는지 */
		boolean hasPrevious(LocalDate today) {
			return !from.minusDays(1).isBefore(earliest(today));
		}

		/** 다음 기간이 이미 시작했는지 */
		boolean hasNext(LocalDate today) {
			return to.isBefore(today);
		}
	}
}
