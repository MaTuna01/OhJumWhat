package com.ohjumwhat.organization;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

@RestController
public class InviteController {

	private final InviteService inviteService;

	public InviteController(InviteService inviteService) {
		this.inviteService = inviteService;
	}

	@GetMapping("/api/invites/{token}")
	InviteResponse get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String token) {
		return inviteService.get(token, loginUser.getUserId());
	}

	@PostMapping("/api/invites/{token}/join")
	JoinResponse join(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String token) {
		return new JoinResponse(inviteService.join(token, loginUser.getUserId()));
	}

	record JoinResponse(Long organizationId) {
	}
}
