package com.ohjumwhat.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiException;

@RestController
public class RankingController {

	private final RankingService rankingService;

	public RankingController(RankingService rankingService) {
		this.rankingService = rankingService;
	}

	/** period: week(기본)·month, date: 그 날짜(YYYY-MM-DD)가 들어 있는 기간(없으면 오늘) */
	@GetMapping("/api/orgs/{orgId}/ranking")
	RankingResponse ranking(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@RequestParam(required = false) String period, @RequestParam(required = false) String date) {
		return rankingService.ranking(orgId, loginUser.getUserId(), RankingPeriod.parse(period), parseDate(date));
	}

	private static LocalDate parseDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(raw.strip());
		}
		catch (DateTimeParseException e) {
			throw ApiException.badRequest("날짜 형식이 올바르지 않아요.");
		}
	}
}
