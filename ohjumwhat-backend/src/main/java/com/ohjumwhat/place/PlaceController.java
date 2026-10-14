package com.ohjumwhat.place;

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

@Tag(name = ApiTags.PLACE)
@RestController
class PlaceController {

	private final PlaceSearchService placeSearchService;

	PlaceController(PlaceSearchService placeSearchService) {
		this.placeSearchService = placeSearchService;
	}

	/** 근처 식당 찾기: 조직 주소 기준 반경 안 음식점 45개까지. 이름·분류가 맞는 곳이 앞이다. q가 비면 가까운 순으로 둘러보기 */
	@Operation(summary = "근처 식당 찾기", description = "식당 찾기 모달(05-F)의 「근처에서 찾기」. 조직 주소를 기준으로 조직 검색 반경 안 음식점을 "
			+ "카카오 로컬에서 45개까지 한 번에 찾는다. q가 있으면 이름·분류에 검색어가 있는 곳(matched)이 앞에 가까운 순, "
			+ "메뉴·태그로만 걸린 곳이 뒤에 가까운 순이고, q가 비면 가까운 순 둘러보기다. "
			+ "결과는 저장하지 않는다(고르면 kakaoPlaceId와 검색어만 메뉴에 붙인다).")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "검색어는 100자 이하로 입력해 주세요.", when = "검색어(공백 정리)가 100자를 넘을 때")
	@ErrorCase(status = 400, message = "검색어는 50자 이하로 입력해 주세요.", when = "검색어(공백 정리)가 50자를 넘을 때")
	@ErrorCase(status = 400, message = "조직 설정에서 조직 주소를 정하면 근처 식당을 찾을 수 있어요.", when = "조직 주소가 없을 때")
	@ErrorCase(status = 400, message = "조직 주소를 지도에서 찾지 못했어요. 조직 설정에서 주소를 확인해 주세요.", when = "조직 주소를 좌표로 바꾸지 못했을 때")
	@ErrorCase(status = 503, message = "근처 식당 찾기를 쓸 수 없어요. 링크를 붙여 주세요.", when = "서버에 카카오 키가 없을 때(지도 꺼짐)")
	@ErrorCase(status = 503, message = "식당 검색이 잠시 안 돼요. 잠시 뒤에 다시 해 주세요.", when = "카카오 응답이 실패했거나 시간이 넘었을 때")
	@GetMapping("/api/orgs/{orgId}/places/search")
	PlaceSearchResponse search(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "검색어(앞뒤·연속 공백 정리, 50자). 비우면 가까운 순 둘러보기", example = "떡볶이")
			@RequestParam(defaultValue = "") String q) {
		return placeSearchService.search(orgId, loginUser.getUserId(), q);
	}

	/** 지도에 올릴 위치: 조직 위치와 메뉴(optionIds, 30개까지)별 식당. optionIds가 없으면 조직 위치만 */
	@Operation(summary = "지도 위치", description = "투표 지도(D05-G·05-G2)와 조직 위치 지도(D07-L)에 올릴 위치. 볼 때마다 조직 주소와 "
			+ "메뉴별 식당을 좌표로 바꿔 준다(좌표는 저장하지 않는다). 카카오 식당은 이름·분류·도로명 주소도 다시 찾아 준다. "
			+ "찾지 못한 메뉴는 빠지고(부분 결과), 서버에 카카오 키가 없으면 center = null, places = []다. "
			+ "다른 조직의 메뉴 ID는 무시한다. 화면은 투표 상세 폴링과 따로 받는다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "한 번에 메뉴 30개까지 볼 수 있어요.", when = "optionIds(중복 제거)가 30개를 넘을 때")
	@GetMapping("/api/orgs/{orgId}/places")
	PlacesResponse places(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "식당 위치를 받을 메뉴 ID(쉼표로 여러 개, 30개까지). 없으면 조직 위치만", example = "100,101")
			@RequestParam(required = false) List<Long> optionIds) {
		return placeSearchService.places(orgId, loginUser.getUserId(), optionIds);
	}
}
