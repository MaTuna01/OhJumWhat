package com.ohjumwhat.user;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiException;

@RestController
public class MeController {

	private final UserService userService;

	private final ProfilePhotoService profilePhotoService;

	public MeController(UserService userService, ProfilePhotoService profilePhotoService) {
		this.userService = userService;
		this.profilePhotoService = profilePhotoService;
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

	/**
	 * 프로필 사진 올리기(multipart 파트 photo). 브라우저가 정사각형으로 잘라 보낸 사진을 서버가 256px JPEG로 다시 만든다.
	 * consumes를 두지 않아, multipart가 아닌 요청도 415 대신 {"message"}가 있는 400으로 답한다(GlobalExceptionHandler).
	 */
	@PostMapping("/api/me/photo")
	MeResponse uploadPhoto(@AuthenticationPrincipal LoginUser loginUser, @RequestParam("photo") MultipartFile photo)
			throws IOException {
		if (photo.isEmpty()) {
			throw ApiException.badRequest("사진 파일을 보내 주세요.");
		}
		return profilePhotoService.upload(loginUser.getUserId(), photo.getBytes());
	}

	/** 올린 사진을 지우고 구글 사진으로 돌아간다. */
	@DeleteMapping("/api/me/photo")
	MeResponse resetPhoto(@AuthenticationPrincipal LoginUser loginUser) {
		return profilePhotoService.resetToGoogle(loginUser.getUserId());
	}

	record NicknameRequest(@Size(max = 100, message = "이름은 20자 이하로 입력해 주세요.") String nickname) {
	}
}
