package com.ohjumwhat.menu;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.STATS)
@RestController
public class MenuStatsController {

	private final MenuStatsService menuStatsService;

	public MenuStatsController(MenuStatsService menuStatsService) {
		this.menuStatsService = menuStatsService;
	}

	/** days가 없으면 전체 기간 */
	@Operation(summary = "메뉴 통계", description = "조직 「통계」 탭(D08). 먹은 메뉴(마감된 투표에서 참여자가 한 명 이상인 메뉴)를 "
			+ "많이 먹은 순으로 30개까지 준다. days를 주면 오늘(한국) 포함 최근 days일, 없으면 전체 기간이다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "기간은 1일에서 366일 사이로 골라 주세요.", when = "days가 1~366이 아닐 때")
	@GetMapping("/api/orgs/{orgId}/menu-stats")
	MenuStatsResponse stats(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "최근 며칠(1~366, 오늘 포함). 없으면 전체 기간", example = "30")
			@RequestParam(required = false) Integer days) {
		return menuStatsService.stats(orgId, loginUser.getUserId(), days);
	}

	@Operation(summary = "메뉴 추천", description = "메뉴 입력창을 비운 채 누르면 보이는 「오늘은 이거 어때요?」(05-R). "
			+ "먹은 적이 있지만 최근 7일(오늘 포함) 안에는 먹지 않은 메뉴를 많이 먹은 순으로 10개까지 주고, "
			+ "같은 이름의 메뉴에 지난번 붙인 식당(lastPlace)도 준다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}/menu-recommendations")
	List<MenuStatsResponse.Recommendation> recommendations(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId) {
		return menuStatsService.recommendations(orgId, loginUser.getUserId());
	}
}
