package com.ohjumwhat.user;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import com.ohjumwhat.common.ApiLimit;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.ME)
@RestController
public class MeController {

	private final UserService userService;

	private final ProfilePhotoService profilePhotoService;

	public MeController(UserService userService, ProfilePhotoService profilePhotoService) {
		this.userService = userService;
		this.profilePhotoService = profilePhotoService;
	}

	@Operation(summary = "내 정보", description = "로그인한 사람의 정보. 화면이 처음 뜰 때 받아 로그인 여부를 정하고(401이면 로그인 화면), "
			+ "마이페이지(D03)·상단 바·막힌 기능 안내(sanctions)에 쓴다. 강제 탈퇴로 회원이 없어졌으면 401이고 남은 세션도 끝낸다.")
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
	@Operation(summary = "별명 바꾸기", description = "마이페이지 「프로필 수정」(03-M2)의 이름. 앞뒤·연속 공백을 정리하고 20자(글자 수)까지 받는다. "
			+ "비우면 별명을 지워 구글 이름으로 돌아간다. 응답은 바뀐 내 정보다.")
	@ErrorCase(status = 400, message = "이름에 쓸 수 없는 문자가 있어요.", when = "제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "이름은 20자 이하로 입력해 주세요.", when = "정리한 별명이 20자를 넘을 때")
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
	@Operation(summary = "한줄 소개·좋아하는 음식 바꾸기", description = "마이페이지 「프로필 수정」(03-M2)의 소개. 통째로 바꾸고, "
			+ "bio가 비면 소개를, foodTags가 비면 음식을 지운다. 태그는 앞의 #과 공백을 정리하고, 띄어쓰기·대소문자만 다른 태그는 "
			+ "먼저 적은 것만 남긴다. 응답은 바뀐 내 정보다.")
	@ErrorCase(status = 400, message = "한줄 소개에 쓸 수 없는 문자가 있어요.", when = "소개에 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "한줄 소개는 50자 이하로 입력해 주세요.", when = "정리한 소개가 50자를 넘을 때")
	@ErrorCase(status = 400, message = "음식 이름에 쓸 수 없는 문자가 있어요.", when = "음식 태그에 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "음식 이름은 10자 이하로 입력해 주세요.", when = "음식 태그 하나가 10자를 넘을 때")
	@ErrorCase(status = 400, message = "좋아하는 음식은 3개까지 적을 수 있어요.", when = "정리한 음식 태그가 3개를 넘을 때")
	@Restricted(Restriction.PROFILE)
	@PutMapping("/api/me/profile")
	MeResponse changeIntro(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody IntroRequest request) {
		return userService.changeIntro(loginUser.getUserId(), request.bio(), request.foodTags());
	}

	/**
	 * 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급) 정하기. 다섯 항목 모두 필수이고 통째로 바꾼다(지우기는 없다).
	 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙과 기획서의 오류 문구는 ProfileDetails가 확인한다.
	 */
	@Operation(summary = "상세 프로필 바꾸기", description = "마이페이지 「프로필 수정」(03-M2·03-M2E)의 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급). "
			+ "다섯 항목 모두 필수이고 통째로 바꾼다(본인은 지울 수 없다). 화면의 입력 순서(MBTI → 퍼스널컬러 → 취미 → 나이 → 직급)대로 "
			+ "확인해 처음 걸린 항목의 문구로 답한다. 응답은 바뀐 내 정보다.")
	@ErrorCase(status = 400, message = "4가지 성향을 모두 선택해 주세요.", when = "MBTI가 E/I·S/N·T/F·J/P 4글자가 아닐 때")
	@ErrorCase(status = 400, message = "퍼스널컬러를 선택해 주세요.", when = "퍼스널컬러가 없거나 정해진 값이 아닐 때")
	@ErrorCase(status = 400, message = "취미에 쓸 수 없는 문자가 있어요.", when = "취미 태그에 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "취미는 10자 이하로 입력해 주세요.", when = "취미 태그 하나가 10자를 넘을 때")
	@ErrorCase(status = 400, message = "취미는 5개까지 적을 수 있어요.", when = "정리한 취미가 5개를 넘을 때")
	@ErrorCase(status = 400, message = "최소 1개 이상의 취미를 등록해 주세요.", when = "취미가 없을 때")
	@ErrorCase(status = 400, message = "올바른 나이를 입력해 주세요. (1~120세)", when = "나이가 없거나 1~120이 아닐 때")
	@ErrorCase(status = 400, message = "직급을 입력해 주세요. (최대 15자)", when = "직급이 비었거나 15자를 넘을 때")
	@ErrorCase(status = 400, message = "직급에 쓸 수 없는 문자가 있어요.", when = "직급에 제어 문자가 있을 때")
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
	@Operation(summary = "프로필 사진 올리기", description = "마이페이지 「프로필 수정」의 사진 맞추기(03-M3). multipart/form-data의 photo 파트로 "
			+ "브라우저가 정사각형(512px)으로 잘라 만든 JPEG·PNG를 보낸다. 서버가 256px JPEG로 다시 그려 저장하고(EXIF 제거), "
			+ "새 사진 주소는 응답의 profileImageUrl이다(올릴 때마다 주소가 바뀐다).",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
							schema = @Schema(type = "object", requiredProperties = "photo"),
							schemaProperties = @SchemaProperty(name = "photo", schema = @Schema(type = "string",
									format = "binary", description = "올릴 사진. JPEG·PNG, 가로·세로 2048px 이하, 5MB 이하")))))
	@ErrorCase(status = 400, message = "이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요.", when = "JPEG·PNG가 아니거나 깨진 파일일 때")
	@ErrorCase(status = 400, message = "사진이 너무 커요. 다른 사진을 골라 주세요.", when = "가로나 세로가 2048px을 넘을 때")
	@Restricted(Restriction.PROFILE)
	@PostMapping("/api/me/photo")
	MeResponse uploadPhoto(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "올릴 사진(multipart 파트 photo). JPEG·PNG, 가로·세로 2048px 이하, 5MB 이하")
			@RequestParam("photo") MultipartFile photo) throws IOException {
		if (photo.isEmpty()) {
			throw ApiException.badRequest("사진 파일을 보내 주세요.");
		}
		return profilePhotoService.upload(loginUser.getUserId(), photo.getBytes());
	}

	/** 올린 사진을 지우고 구글 사진으로 돌아간다. */
	@Operation(summary = "구글 사진으로 되돌리기", description = "올린 사진을 지우고 구글 프로필 사진으로 돌아간다(03-M2). "
			+ "올린 사진이 없어도 그대로 성공한다. 응답은 바뀐 내 정보다.")
	@Restricted(Restriction.PROFILE)
	@DeleteMapping("/api/me/photo")
	MeResponse resetPhoto(@AuthenticationPrincipal LoginUser loginUser) {
		return profilePhotoService.resetToGoogle(loginUser.getUserId());
	}

	@Schema(name = "MeNicknameRequest", description = "별명 바꾸기")
	record NicknameRequest(
			@Schema(description = "별명(앞뒤·연속 공백 정리, 20자). 비우거나 null이면 구글 이름으로 돌아간다", example = "김오점",
					nullable = true)
			@ApiLimit(20)
			@Size(max = 100, message = "이름은 20자 이하로 입력해 주세요.") String nickname) {
	}

	@Schema(name = "MeIntroRequest", description = "한줄 소개·좋아하는 음식(통째로 바꾼다)")
	record IntroRequest(
			@Schema(description = "한줄 소개(앞뒤·연속 공백 정리, 50자). 비우거나 null이면 지운다", example = "점심은 무조건 국밥",
					nullable = true)
			@ApiLimit(50)
			@Size(max = 200, message = "한줄 소개는 50자 이하로 입력해 주세요.") String bio,
			@Schema(description = "좋아하는 음식 태그(적은 순서, 하나 10자, 최대 3개, 앞의 # 제거). 개수·길이는 정리(# 제거·중복 제거)한 뒤 기준이고, 명세의 한도도 정리한 값 기준이다. 비우거나 null이면 지운다",
					example = "[\"국밥\", \"떡볶이\"]", nullable = true)
			@ApiLimit(value = 3, items = 10)
			@Size(max = 20, message = "좋아하는 음식은 3개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "음식 이름은 10자 이하로 입력해 주세요.") String> foodTags) {
	}

	@Schema(name = "MeDetailsRequest", description = "상세 프로필(다섯 항목 모두 필수, 통째로 바꾼다)")
	record DetailsRequest(
			@Schema(description = "MBTI: E/I·S/N·T/F·J/P를 하나씩 고른 4글자(대소문자 상관없음, 대문자로 저장)", example = "ENFP",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 20, message = "4가지 성향을 모두 선택해 주세요.") String mbti,
			@Schema(description = "퍼스널컬러: SPRING_WARM 봄 웜, SUMMER_COOL 여름 쿨, AUTUMN_WARM 가을 웜, WINTER_COOL 겨울 쿨"
					+ "(대소문자 상관없음)", example = "SPRING_WARM",
					allowableValues = { "SPRING_WARM", "SUMMER_COOL", "AUTUMN_WARM", "WINTER_COOL" },
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 40, message = "퍼스널컬러를 선택해 주세요.") String personalColor,
			@Schema(description = "취미 태그(적은 순서, 하나 10자, 1~5개). 정리 규칙과 한도 기준은 좋아하는 음식과 같다(정리한 뒤 기준)",
					example = "[\"등산\", \"보드게임\"]", requiredMode = Schema.RequiredMode.REQUIRED)
			@ApiLimit(value = 5, items = 10)
			@Size(max = 20, message = "취미는 5개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "취미는 10자 이하로 입력해 주세요.") String> hobbies,
			@Schema(description = "나이(1~120 정수)", example = "29", requiredMode = Schema.RequiredMode.REQUIRED)
			Integer age,
			@Schema(description = "직급(앞뒤·연속 공백 정리, 1~15자)", example = "대리", requiredMode = Schema.RequiredMode.REQUIRED)
			@ApiLimit(15)
			@Size(max = 100, message = "직급을 입력해 주세요. (최대 15자)") String jobTitle) {
	}
}
