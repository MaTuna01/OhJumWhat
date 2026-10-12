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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 본인의 제재 안내. 지금 걸려 있는 제재는 GET /api/me의 sanctions로 받는다. */
@Tag(name = ApiTags.SANCTION)
@RestController
@RequestMapping("/api/sanctions")
class SanctionController {

	private final SanctionService sanctionService;

	SanctionController(SanctionService sanctionService) {
		this.sanctionService = sanctionService;
	}

	/** 아직 보지 않은 제재 안내(앱을 열거나 화면이 바뀔 때 안내 창으로 보여준다) */
	@Operation(summary = "안 본 제재 안내·신고 결과", description = "앱을 열거나 화면이 바뀔 때 띄울 안내 창의 재료. 아직 보지 않은 내 제재(00-S)와 "
			+ "내 사람 신고의 처리 결과(00-R)를 오래된 순으로 각각 20건까지 준다. 그사이 끝났거나 해제된 제재도 status와 함께 준다. "
			+ "지금 걸려 있는 제재는 GET /api/me의 sanctions로 받는다.")
	@GetMapping("/alerts")
	SanctionAlertsResponse alerts(@AuthenticationPrincipal LoginUser loginUser) {
		return sanctionService.alerts(loginUser.getUserId());
	}

	/** 안내 창에서 넘겨 본 제재들(1~50개). 제재 중에도 된다. */
	@Operation(summary = "제재 안내 봤음", description = "제재 안내 창(00-S)을 끝까지 넘기면 본 제재 ID를 보낸다(1~50개). 내 것이고 아직 보지 않은 것만 "
			+ "바뀌고, 남의 ID나 이미 본 ID는 무시한다. 제재 중에도 된다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 400, message = "확인한 안내가 올바르지 않아요.", when = "ids가 비었거나 50개를 넘거나 null이 섞였을 때")
	@Unrestricted
	@PostMapping("/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody SeenRequest request) {
		sanctionService.markSeen(loginUser.getUserId(), request.ids());
	}

	/** 개수·값 규칙은 SanctionService가 확인한다. 여기서는 터무니없이 큰 요청만 막는다. */
	@Schema(name = "SanctionSeenRequest", description = "본 제재 안내")
	record SeenRequest(
			@Schema(description = "본 제재 ID(SanctionNotice.id, 1~50개)", example = "[3, 4]", requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 1000, message = "확인한 안내가 올바르지 않아요.") List<Long> ids) {
	}
}
