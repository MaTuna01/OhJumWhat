package com.ohjumwhat.push;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Unrestricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 이 기기에서 알림 받기. fid는 브라우저의 Firebase 설치 ID다. */
@Tag(name = ApiTags.PUSH)
@RestController
@RequestMapping("/api/push/devices")
class PushController {

	private final PushDeviceService deviceService;

	PushController(PushDeviceService deviceService) {
		this.deviceService = deviceService;
	}

	/** 켜기(다시 켜도 된다). 기기는 지금 로그인(세션)에 묶여 로그아웃하면 함께 지워진다. */
	@Operation(summary = "이 기기에서 알림 켜기", description = "마이페이지 「알림」 카드(03-N6·D03-N6)의 스위치를 켜거나, 켜 둔 사람이 앱을 열 때 "
			+ "조용히 다시 등록한다(다시 보내도 된다). 기기는 지금 로그인(세션)에 묶여 로그아웃·세션 만료 때 함께 지워지고, "
			+ "같은 브라우저에서 다른 계정이 켜면 그 사람에게 옮겨 간다. 한 사람당 최근 10대까지 둔다. "
			+ "로그인 세션이 서버에 없으면(만료·로그아웃 직후) 401이다.")
	@ErrorCase(status = 400, message = "알림을 받을 기기 정보가 올바르지 않아요.", when = "FID가 영문·숫자·-·_ 16~64자가 아닐 때")
	@ErrorCase(status = 409, message = "지금은 알림을 켤 수 없어요.", when = "서버의 웹 푸시가 꺼져 있을 때(/api/config의 push가 null)")
	@Unrestricted
	@PutMapping("/{fid}")
	PushDeviceResponse register(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "이 브라우저의 Firebase 설치 ID(FID, 영문·숫자·-·_ 16~64자)", example = "cXk3Rz9Tq0uVw1Yb2Nd4Ef")
			@PathVariable String fid, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return new PushDeviceResponse(
				deviceService.register(loginUser.getUserId(), session == null ? null : session.getId(), fid));
	}

	/** 끄기. 내 기기만 지운다(없어도 204). */
	@Operation(summary = "이 기기에서 알림 끄기", description = "마이페이지 「알림」 카드의 스위치를 끄거나 로그아웃하기 전에 부른다. "
			+ "내 기기만 지우고, 없어도 204(본문 없음)다.")
	@Unrestricted
	@DeleteMapping("/{fid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unregister(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "이 브라우저의 Firebase 설치 ID(FID)", example = "cXk3Rz9Tq0uVw1Yb2Nd4Ef") @PathVariable String fid) {
		deviceService.unregister(loginUser.getUserId(), fid);
	}

	/** @param created 새로 등록했으면 true(이미 있던 기기를 다시 켰거나 다른 계정에서 옮겨 왔으면 false) */
	@Schema(description = "알림 기기 등록 결과")
	record PushDeviceResponse(
			@Schema(description = "새로 등록했으면 true. 이미 있던 기기를 다시 켰거나 다른 계정에서 옮겨 왔으면 false", example = "true")
			boolean created) {
	}
}
