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

/** 이 기기에서 알림 받기. fid는 브라우저의 Firebase 설치 ID다. */
@RestController
@RequestMapping("/api/push/devices")
class PushController {

	private final PushDeviceService deviceService;

	PushController(PushDeviceService deviceService) {
		this.deviceService = deviceService;
	}

	/** 켜기(다시 켜도 된다). 기기는 지금 로그인(세션)에 묶여 로그아웃하면 함께 지워진다. */
	@PutMapping("/{fid}")
	PushDeviceResponse register(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String fid,
			HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return new PushDeviceResponse(
				deviceService.register(loginUser.getUserId(), session == null ? null : session.getId(), fid));
	}

	/** 끄기. 내 기기만 지운다(없어도 204). */
	@DeleteMapping("/{fid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unregister(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String fid) {
		deviceService.unregister(loginUser.getUserId(), fid);
	}

	/** @param created 새로 등록했으면 true(이미 있던 기기를 다시 켰거나 다른 계정에서 옮겨 왔으면 false) */
	record PushDeviceResponse(boolean created) {
	}
}
