package com.ohjumwhat.organization;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.place.PlaceLinkResolver;
import com.ohjumwhat.place.PlaceLinks;
import com.ohjumwhat.place.PlaceSearchService;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.Unrestricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.ORG)
@RestController
public class OrganizationController {

	private final OrganizationService organizationService;

	private final PlaceLinkResolver placeLinkResolver;

	private final PlaceSearchService placeSearchService;

	public OrganizationController(OrganizationService organizationService, PlaceLinkResolver placeLinkResolver,
			PlaceSearchService placeSearchService) {
		this.organizationService = organizationService;
		this.placeLinkResolver = placeLinkResolver;
		this.placeSearchService = placeSearchService;
	}

	@Operation(summary = "내 조직 목록", description = "마이페이지(D03)의 「내 조직」. 내가 속한 조직과 인원, 오늘 진행 중인 투표가 있는지를 준다. "
			+ "속한 조직이 없으면 빈 목록이다.")
	@GetMapping("/api/me/orgs")
	List<MyOrganizationResponse> myOrganizations(@AuthenticationPrincipal LoginUser loginUser) {
		return organizationService.myOrganizations(loginUser.getUserId());
	}

	@Operation(summary = "조직 만들기", description = "조직을 만들고 만든 사람을 첫 멤버로 넣는다. 이름은 앞뒤 공백을 지워 저장하고, "
			+ "초대 링크 토큰(inviteToken)을 새로 만든다.")
	@Restricted(Restriction.SUSPEND)
	@PostMapping("/api/orgs")
	@ResponseStatus(HttpStatus.CREATED)
	OrganizationResponse create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody OrganizationNameRequest request) {
		return organizationService.create(loginUser.getUserId(), request.name());
	}

	@Operation(summary = "조직 정보", description = "조직 홈(D04)·조직 설정(D07)이 쓰는 조직 정보(이름·인원·초대 토큰·위치). "
			+ "부를 때마다 「최근 들어간 조직」을 이 조직으로 바꾼다(로그인 뒤 이 조직으로 간다).")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}")
	OrganizationResponse get(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId) {
		return organizationService.visit(orgId, loginUser.getUserId());
	}

	@Operation(summary = "조직 이름 바꾸기", description = "조직 설정(D07)의 이름. 멤버 누구나 바꾼다. 앞뒤 공백을 지워 저장한다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@Restricted(Restriction.SUSPEND)
	@PatchMapping("/api/orgs/{orgId}")
	OrganizationResponse rename(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Valid @RequestBody OrganizationNameRequest request) {
		return organizationService.rename(orgId, loginUser.getUserId(), request.name());
	}

	/**
	 * 조직 위치(검색 지역, 장소 링크·이름, 조직 주소, 검색 반경)를 통째로 바꾼다.
	 * 장소 링크 확인(naver.me 요청)과 조직 주소 확인(카카오 로컬)은 DB 트랜잭션 밖에서 한다.
	 */
	@Operation(summary = "조직 위치 바꾸기", description = "조직 설정의 조직 위치(07-L·D07-L). 멤버 누구나 통째로 바꾸고, 빈 값은 지운다. "
			+ "조직 주소는 근처 식당 찾기·지도·거리의 기준점이라 저장 전에 카카오로 찾을 수 있는지 확인한다(카카오를 쓸 수 없으면 확인 없이 저장). "
			+ "장소 링크는 메뉴의 식당 링크와 같은 규칙이고, naver.me 단축 링크는 서버가 정식 링크로 바꾼다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "주소를 찾지 못했어요. 도로명 주소로 적어 주세요.", when = "카카오가 조직 주소를 찾지 못했을 때")
	@ErrorCase(status = 400, message = "주소는 200자 이하로 입력해 주세요.", when = "정리한 주소가 200자를 넘을 때")
	@ErrorCase(status = 400, message = "식당 이름은 100자 이하로 입력해 주세요.", when = "정리한 장소 이름이 100자를 넘을 때")
	@ErrorCase(status = 400, message = "링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요.", when = "장소 링크가 http/https 주소가 아닐 때")
	@ErrorCase(status = 400, message = "링크는 500자 이하로 입력해 주세요.", when = "정리한 링크가 500자를 넘을 때")
	@ErrorCase(status = 400, message = "공유 링크를 찾을 수 없어요. 네이버 지도에서 링크를 다시 복사해 주세요.", when = "naver.me가 없는 코드라고 답할 때")
	@ErrorCase(status = 400, message = "검색 반경은 500m, 1km, 2km 중에서 골라 주세요.", when = "searchRadius가 500·1000·2000이 아닐 때")
	@Restricted(Restriction.SUSPEND)
	@PutMapping("/api/orgs/{orgId}/location")
	OrganizationResponse changeLocation(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Valid @RequestBody LocationRequest request) {
		Long userId = loginUser.getUserId();
		String officeAddress = PlaceLinks.address(request.officeAddress());
		placeSearchService.verifyOfficeAddress(orgId, userId, officeAddress);
		return organizationService.changeLocation(orgId, userId, request.area(),
				placeLinkResolver.place(request.officeLink(), request.officeName()), officeAddress,
				request.searchRadius());
	}

	@Operation(summary = "멤버 목록", description = "조직 홈 사이드·조직 설정(D07)의 「멤버 N명」과 멤버 프로필 모달(07-P)의 소개·상세 프로필. "
			+ "들어온 순서로 준다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}/members")
	List<MemberResponse> members(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId) {
		return organizationService.members(orgId, loginUser.getUserId());
	}

	@Operation(summary = "조직 탈퇴", description = "조직 설정(D07)의 「조직 나가기」. 진행 중인 투표의 내 응답은 지우고 마감된 투표 기록은 남긴다. "
			+ "마지막 멤버였으면 조직과 그 안의 데이터를 모두 지운다(organizationDeleted = true). 제재 중에도 할 수 있다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@Unrestricted
	@DeleteMapping("/api/orgs/{orgId}/membership")
	LeaveResponse leave(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId) {
		return organizationService.leave(orgId, loginUser.getUserId());
	}
}
