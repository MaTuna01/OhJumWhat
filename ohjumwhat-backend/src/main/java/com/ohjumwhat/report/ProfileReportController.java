package com.ohjumwhat.report;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.sanction.Unrestricted;

/** 사람 신고와 신고 결과 확인. 아직 보지 않은 결과는 GET /api/sanctions/alerts의 reportResults로 받는다. */
@RestController
@RequestMapping("/api")
class ProfileReportController {

	private final ProfileReportService reportService;

	ProfileReportController(ProfileReportService reportService) {
		this.reportService = reportService;
	}

	/** 같은 조직 멤버를 신고한다. 처리 전 신고가 이미 있으면 그대로다. 제재 중에도 된다. */
	@Unrestricted
	@PostMapping("/users/{userId}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long userId,
			@Valid @RequestBody ReportRequest request) {
		reportService.report(loginUser.getUserId(), userId, request.reason(), request.detail());
	}

	/** 결과 창에서 넘겨 본 신고 결과들(1~50개). 제재 중에도 된다. */
	@Unrestricted
	@PostMapping("/profile-reports/results/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody SeenRequest request) {
		reportService.markResultsSeen(loginUser.getUserId(), request.ids());
	}

	/**
	 * 값 규칙(사유, 설명 100자)은 ProfileReportService가 확인한다. 여기서는 터무니없이 큰 요청만 막는다.
	 *
	 * @param reason 사유 분류(SanctionReason 이름, 필수)
	 * @param detail 설명(선택, 한 줄)
	 */
	record ReportRequest(@Size(max = 40, message = "무엇이 문제인지 골라 주세요.") String reason,
			@Size(max = 400, message = "100자 이하로 입력해 주세요.") String detail) {
	}

	/** 개수·값 규칙은 ProfileReportService가 확인한다. */
	record SeenRequest(@Size(max = 1000, message = "확인한 안내가 올바르지 않아요.") List<Long> ids) {
	}
}
