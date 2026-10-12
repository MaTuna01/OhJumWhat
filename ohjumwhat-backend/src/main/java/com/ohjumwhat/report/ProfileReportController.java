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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Unrestricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 사람 신고와 신고 결과 확인. 아직 보지 않은 결과는 GET /api/sanctions/alerts의 reportResults로 받는다. */
@Tag(name = ApiTags.SANCTION)
@RestController
@RequestMapping("/api")
class ProfileReportController {

	private final ProfileReportService reportService;

	ProfileReportController(ProfileReportService reportService) {
		this.reportService = reportService;
	}

	/** 같은 조직 멤버를 신고한다. 처리 전 신고가 이미 있으면 그대로다. 제재 중에도 된다. */
	@Operation(summary = "사람 신고", description = "멤버 프로필 모달의 「이 사람 신고하기」(07-P6) → 신고 창(07-M5). 같은 조직 멤버를 (프로필 내용과 행동으로) "
			+ "신고한다. 같은 사람에 대한 처리 전 신고가 이미 있으면 다시 보내도 그대로 204다(처리된 뒤에는 다시 신고할 수 있다). "
			+ "신고된 사람에게는 알리지 않고, 관리자가 처리하면 신고한 사람에게 결과(00-R)를 알린다. 제재 중에도 된다. 확인 순서는 "
			+ "나 자신(400) → 같은 조직이 아님(404) → 사유(400) → 설명(400)이다.")
	@ErrorCase(status = 400, message = "나 자신은 신고할 수 없어요.", when = "대상이 나일 때")
	@ErrorCase(status = 404, message = "사람을 찾을 수 없어요.", when = "대상이 없거나 나와 같은 조직이 아닐 때")
	@ErrorCase(status = 400, message = "무엇이 문제인지 골라 주세요.", when = "reason이 없거나 SanctionReason이 아닐 때")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "설명에 제어 문자(줄바꿈 포함)가 있을 때")
	@Unrestricted
	@PostMapping("/users/{userId}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "신고할 회원 ID", example = "8") @PathVariable Long userId,
			@Valid @RequestBody ReportRequest request) {
		reportService.report(loginUser.getUserId(), userId, request.reason(), request.detail());
	}

	/** 결과 창에서 넘겨 본 신고 결과들(1~50개). 제재 중에도 된다. */
	@Operation(summary = "신고 결과 봤음", description = "신고 결과 창(00-R)을 넘겨 본 신고 ID를 보낸다(1~50개). 내가 신고했고 처리된 것만 바뀌고, "
			+ "남의 ID·처리 전·이미 본 ID는 무시한다. 제재 중에도 된다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 400, message = "확인한 안내가 올바르지 않아요.", when = "ids가 비었거나 50개를 넘거나 null이 섞였을 때")
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
	@Schema(name = "ProfileReportRequest", description = "사람 신고")
	record ReportRequest(
			@Schema(description = "사유 분류(SanctionReason 이름, 대소문자 구분)", example = "PROFILE",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 40, message = "무엇이 문제인지 골라 주세요.") String reason,
			@Schema(description = "설명(선택, 한 줄 100자, 줄바꿈 불가)", example = "프로필 사진이 부적절해요", nullable = true)
			@Size(max = 400, message = "100자 이하로 입력해 주세요.") String detail) {
	}

	/** 개수·값 규칙은 ProfileReportService가 확인한다. */
	@Schema(name = "ReportResultSeenRequest", description = "본 신고 결과")
	record SeenRequest(
			@Schema(description = "본 신고 ID(ReportResult.id, 1~50개)", example = "[5]", requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 1000, message = "확인한 안내가 올바르지 않아요.") List<Long> ids) {
	}
}
