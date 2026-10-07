package com.ohjumwhat.sanction;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

/** 본인의 제재 안내. 지금 걸려 있는 제재는 GET /api/me의 sanctions로 받는다. */
@RestController
@RequestMapping("/api/sanctions")
class SanctionController {

	private final SanctionService sanctionService;

	SanctionController(SanctionService sanctionService) {
		this.sanctionService = sanctionService;
	}

	/** 아직 보지 않은 제재 안내(앱을 열거나 화면이 바뀔 때 안내 창으로 보여준다) */
	@GetMapping("/alerts")
	SanctionAlertsResponse alerts(@AuthenticationPrincipal LoginUser loginUser) {
		return sanctionService.alerts(loginUser.getUserId());
	}

	/** 안내 창에서 넘겨 본 제재들(1~50개). 제재 중에도 된다. */
	@Unrestricted
	@PostMapping("/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody SeenRequest request) {
		sanctionService.markSeen(loginUser.getUserId(), request.ids());
	}

	/** 개수·값 규칙은 SanctionService가 확인한다. 여기서는 터무니없이 큰 요청만 막는다. */
	record SeenRequest(@Size(max = 1000, message = "확인한 안내가 올바르지 않아요.") List<Long> ids) {
	}
}
