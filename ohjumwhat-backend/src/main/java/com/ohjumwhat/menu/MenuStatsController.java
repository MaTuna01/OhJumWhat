package com.ohjumwhat.menu;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

@RestController
public class MenuStatsController {

	private final MenuStatsService menuStatsService;

	public MenuStatsController(MenuStatsService menuStatsService) {
		this.menuStatsService = menuStatsService;
	}

	/** days가 없으면 전체 기간 */
	@GetMapping("/api/orgs/{orgId}/menu-stats")
	MenuStatsResponse stats(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@RequestParam(required = false) Integer days) {
		return menuStatsService.stats(orgId, loginUser.getUserId(), days);
	}

	@GetMapping("/api/orgs/{orgId}/menu-recommendations")
	List<MenuStatsResponse.MenuStat> recommendations(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long orgId) {
		return menuStatsService.recommendations(orgId, loginUser.getUserId());
	}
}
