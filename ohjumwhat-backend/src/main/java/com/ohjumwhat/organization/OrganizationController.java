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
import com.ohjumwhat.place.PlaceLinkResolver;

@RestController
public class OrganizationController {

	private final OrganizationService organizationService;

	private final PlaceLinkResolver placeLinkResolver;

	public OrganizationController(OrganizationService organizationService, PlaceLinkResolver placeLinkResolver) {
		this.organizationService = organizationService;
		this.placeLinkResolver = placeLinkResolver;
	}

	@GetMapping("/api/me/orgs")
	List<MyOrganizationResponse> myOrganizations(@AuthenticationPrincipal LoginUser loginUser) {
		return organizationService.myOrganizations(loginUser.getUserId());
	}

	@PostMapping("/api/orgs")
	@ResponseStatus(HttpStatus.CREATED)
	OrganizationResponse create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody OrganizationNameRequest request) {
		return organizationService.create(loginUser.getUserId(), request.name());
	}

	@GetMapping("/api/orgs/{orgId}")
	OrganizationResponse get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId) {
		return organizationService.visit(orgId, loginUser.getUserId());
	}

	@PatchMapping("/api/orgs/{orgId}")
	OrganizationResponse rename(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@Valid @RequestBody OrganizationNameRequest request) {
		return organizationService.rename(orgId, loginUser.getUserId(), request.name());
	}

	/** 조직 위치(검색 지역, 회사 위치)를 통째로 바꾼다. 회사 링크 확인(naver.me 요청)은 DB 트랜잭션 밖에서 한다. */
	@PutMapping("/api/orgs/{orgId}/location")
	OrganizationResponse changeLocation(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@Valid @RequestBody LocationRequest request) {
		return organizationService.changeLocation(orgId, loginUser.getUserId(), request.area(),
				placeLinkResolver.place(request.officeLink(), request.officeName()));
	}

	@GetMapping("/api/orgs/{orgId}/members")
	List<MemberResponse> members(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId) {
		return organizationService.members(orgId, loginUser.getUserId());
	}

	@DeleteMapping("/api/orgs/{orgId}/membership")
	LeaveResponse leave(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId) {
		return organizationService.leave(orgId, loginUser.getUserId());
	}
}
