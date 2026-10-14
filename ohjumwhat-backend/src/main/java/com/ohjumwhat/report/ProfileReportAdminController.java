package com.ohjumwhat.report;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자 콘솔의 사람 신고. /api/admin/**는 관리자만 호출할 수 있다(SecurityConfig). 제재로 처리하는 것은 제재 API
 * (POST /api/admin/users/{id}/sanctions)가 함께 한다.
 */
@Tag(name = ApiTags.ADMIN_REPORT)
@RestController
@RequestMapping("/api/admin/profile-reports")
class ProfileReportAdminController {

	private final ProfileReportService reportService;

	ProfileReportAdminController(ProfileReportService reportService) {
		this.reportService = reportService;
	}

	/** 신고 목록(최신순 100건). status: open(기본, 처리 전만)·all */
	@Operation(summary = "사람 신고 목록", description = "「신고」 탭의 「프로필」(A09c, `?type=profile`). 최신순 100건이고, 신고할 때의 프로필(snapshot)과 "
			+ "지금 프로필(current)을 함께 준다. 제재로 처리하려면 「제재하기」(POST /api/admin/users/{userId}/sanctions)를 부르면 "
			+ "그 사람의 처리 전 신고가 모두 「조치함」이 된다.")
	@GetMapping
	List<ProfileReportRow> list(
			@Parameter(description = "open(기본, 처리 전만)·all(전체). 그 밖의 값은 open으로 본다", example = "open")
			@RequestParam(defaultValue = "open") String status) {
		return reportService.list(!"all".equalsIgnoreCase(status));
	}

	/** 문제 없음. 처리한 신고 한 줄을 돌려준다. */
	@Operation(summary = "사람 신고 문제 없음", description = "신고를 「문제 없음」(DISMISSED)으로 처리하고 신고한 사람에게 결과를 알린다. "
			+ "이미 「문제 없음」이면 그대로다. 응답은 처리한 신고 한 줄이다.")
	@ErrorCase(status = 404, message = "신고를 찾을 수 없어요.", when = "신고가 없을 때")
	@ErrorCase(status = 409, message = "이미 처리한 신고예요.", when = "제재·탈퇴 처리 등 다른 결과로 처리했을 때")
	@PostMapping("/{reportId}/dismiss")
	ProfileReportRow dismiss(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "사람 신고 ID", example = "5") @PathVariable Long reportId) {
		return reportService.dismiss(admin.getUserId(), reportId);
	}
}
