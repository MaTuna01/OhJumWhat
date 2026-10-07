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

/**
 * 관리자 콘솔의 사람 신고. /api/admin/**는 관리자만 호출할 수 있다(SecurityConfig). 제재로 처리하는 것은 제재 API
 * (POST /api/admin/users/{id}/sanctions)가 함께 한다.
 */
@RestController
@RequestMapping("/api/admin/profile-reports")
class ProfileReportAdminController {

	private final ProfileReportService reportService;

	ProfileReportAdminController(ProfileReportService reportService) {
		this.reportService = reportService;
	}

	/** 신고 목록(최신순 100건). status: open(기본, 처리 전만)·all */
	@GetMapping
	List<ProfileReportRow> list(@RequestParam(defaultValue = "open") String status) {
		return reportService.list(!"all".equalsIgnoreCase(status));
	}

	/** 문제 없음. 처리한 신고 한 줄을 돌려준다. */
	@PostMapping("/{reportId}/dismiss")
	ProfileReportRow dismiss(@AuthenticationPrincipal LoginUser admin, @PathVariable Long reportId) {
		return reportService.dismiss(admin.getUserId(), reportId);
	}
}
