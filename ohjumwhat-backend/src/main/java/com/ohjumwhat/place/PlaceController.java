package com.ohjumwhat.place;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

@RestController
class PlaceController {

	private final PlaceSearchService placeSearchService;

	PlaceController(PlaceSearchService placeSearchService) {
		this.placeSearchService = placeSearchService;
	}

	/** 지도에 올릴 위치: 회사와 메뉴(optionIds, 30개까지)별 식당. optionIds가 없으면 회사 위치만 */
	@GetMapping("/api/orgs/{orgId}/places")
	PlacesResponse places(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@RequestParam(required = false) List<Long> optionIds) {
		return placeSearchService.places(orgId, loginUser.getUserId(), optionIds);
	}
}
