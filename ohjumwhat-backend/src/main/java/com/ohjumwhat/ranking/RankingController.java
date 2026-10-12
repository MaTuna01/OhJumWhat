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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.RANKING)
@RestController
public class RankingController {

	private final RankingService rankingService;

	public RankingController(RankingService rankingService) {
		this.rankingService = rankingService;
	}

	/** period: week(기본)·month, date: 그 날짜(YYYY-MM-DD)가 들어 있는 기간(없으면 오늘) */
	@Operation(summary = "메뉴 메이커 랭킹", description = "조직 「랭킹」 탭(D11)과 조직 홈 랭킹 카드(D04-R). 기간(투표 날짜 기준 주 월~일, "
			+ "달 1일~말일) 안에 마감된 투표에서 채택된 메뉴를 추가한 사람의 순위다. 지금 멤버만 넣고, 같은 횟수는 같은 순위(1, 1, 3)다. "
			+ "이번 달 1일의 12개월 전이 들어 있는 기간까지 볼 수 있다. 「이달의 메뉴 메이커」는 지난달 월간 1위다(period=month, date=지난달).")
	@ErrorCase(status = 400, message = "기간은 week나 month예요.", when = "period가 week·month가 아닐 때")
	@ErrorCase(status = 400, message = "날짜 형식이 올바르지 않아요.", when = "date가 YYYY-MM-DD가 아닐 때")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "아직 오지 않은 기간이에요.", when = "date가 오늘(한국)보다 뒤일 때")
	@ErrorCase(status = 400, message = "랭킹은 12개월 전까지 볼 수 있어요.", when = "기간이 이번 달 1일의 12개월 전보다 앞일 때")
	@GetMapping("/api/orgs/{orgId}/ranking")
	RankingResponse ranking(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "기간: week(주간, 기본)·month(월간). 대소문자 무시", example = "week")
			@RequestParam(required = false) String period,
			@Parameter(description = "이 날짜(한국, YYYY-MM-DD)가 들어 있는 기간. 없으면 오늘(서버의 오늘)", example = "2026-10-12")
			@RequestParam(required = false) String date) {
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
