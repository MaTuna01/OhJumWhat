package com.ohjumwhat.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiException;

@RestController
public class MeController {

	private final UserService userService;

	public MeController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/me")
	MeResponse me(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
		try {
			return userService.getMe(loginUser.getUserId());
		}
		catch (ApiException e) {
			// 회원이 없어졌다면(강제 탈퇴) 남아 있는 세션을 끝낸다.
			if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.invalidate();
				}
			}
			throw e;
		}
	}
}
