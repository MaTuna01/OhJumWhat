package com.ohjumwhat.user;

import java.io.IOException;
import java.util.List;

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
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

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
	@Restricted(Restriction.PROFILE)
	@PutMapping("/api/me/nickname")
	MeResponse changeNickname(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody NicknameRequest request) {
		return userService.changeNickname(loginUser.getUserId(), request.nickname());
	}

	/**
	 * 한줄 소개와 좋아하는 음식 정하기(통째로 바꾼다). bio가 비면 소개를, foodTags가 비면 음식을 지운다.
	 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙(50자, 10자·3개)은 ProfileIntro가 확인한다.
	 */
	@Restricted(Restriction.PROFILE)
	@PutMapping("/api/me/profile")
	MeResponse changeIntro(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody IntroRequest request) {
		return userService.changeIntro(loginUser.getUserId(), request.bio(), request.foodTags());
	}

	/**
	 * 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급) 정하기. 다섯 항목 모두 필수이고 통째로 바꾼다(지우기는 없다).
	 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙과 기획서의 오류 문구는 ProfileDetails가 확인한다.
	 */
	@Restricted(Restriction.PROFILE)
	@PutMapping("/api/me/profile/details")
	MeResponse changeDetails(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody DetailsRequest request) {
		return userService.changeDetails(loginUser.getUserId(), request.mbti(), request.personalColor(),
				request.hobbies(), request.age(), request.jobTitle());
	}

	/**
	 * 프로필 사진 올리기(multipart 파트 photo). 브라우저가 정사각형으로 잘라 보낸 사진을 서버가 256px JPEG로 다시 만든다.
	 * consumes를 두지 않아, multipart가 아닌 요청도 415 대신 {"message"}가 있는 400으로 답한다(GlobalExceptionHandler).
	 */
	@Restricted(Restriction.PROFILE)
	@PostMapping("/api/me/photo")
	MeResponse uploadPhoto(@AuthenticationPrincipal LoginUser loginUser, @RequestParam("photo") MultipartFile photo)
			throws IOException {
		if (photo.isEmpty()) {
			throw ApiException.badRequest("사진 파일을 보내 주세요.");
		}
		return profilePhotoService.upload(loginUser.getUserId(), photo.getBytes());
	}

	/** 올린 사진을 지우고 구글 사진으로 돌아간다. */
	@Restricted(Restriction.PROFILE)
	@DeleteMapping("/api/me/photo")
	MeResponse resetPhoto(@AuthenticationPrincipal LoginUser loginUser) {
		return profilePhotoService.resetToGoogle(loginUser.getUserId());
	}

	record NicknameRequest(@Size(max = 100, message = "이름은 20자 이하로 입력해 주세요.") String nickname) {
	}

	record IntroRequest(@Size(max = 200, message = "한줄 소개는 50자 이하로 입력해 주세요.") String bio,
			@Size(max = 20, message = "좋아하는 음식은 3개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "음식 이름은 10자 이하로 입력해 주세요.") String> foodTags) {
	}

	record DetailsRequest(@Size(max = 20, message = "4가지 성향을 모두 선택해 주세요.") String mbti,
			@Size(max = 40, message = "퍼스널컬러를 선택해 주세요.") String personalColor,
			@Size(max = 20, message = "취미는 5개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "취미는 10자 이하로 입력해 주세요.") String> hobbies,
			Integer age,
			@Size(max = 100, message = "직급을 입력해 주세요. (최대 15자)") String jobTitle) {
	}
}
