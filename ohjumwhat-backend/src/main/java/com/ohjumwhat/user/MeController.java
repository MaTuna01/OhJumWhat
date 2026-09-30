package com.ohjumwhat.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

@RestController
public class MeController {

	private final UserService userService;

	public MeController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/me")
	MeResponse me(@AuthenticationPrincipal LoginUser loginUser) {
		return userService.getMe(loginUser.getUserId());
	}
}
