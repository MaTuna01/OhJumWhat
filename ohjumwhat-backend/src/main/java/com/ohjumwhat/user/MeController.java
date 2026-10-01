package com.ohjumwhat.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	/** 별명 정하기. nickname이 비어 있으면 구글 이름으로 돌아간다. */
	@PutMapping("/api/me/nickname")
	MeResponse changeNickname(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody NicknameRequest request) {
		return userService.changeNickname(loginUser.getUserId(), request.nickname());
	}

	record NicknameRequest(@Size(max = 100, message = "이름은 20자 이하로 입력해 주세요.") String nickname) {
	}
}
