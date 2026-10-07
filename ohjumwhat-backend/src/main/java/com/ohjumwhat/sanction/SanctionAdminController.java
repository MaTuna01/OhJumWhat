package com.ohjumwhat.sanction;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

/** 관리자 콘솔의 제재. /api/admin/**는 관리자만 호출할 수 있다(SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
class SanctionAdminController {

	private final SanctionService sanctionService;

	SanctionAdminController(SanctionService sanctionService) {
		this.sanctionService = sanctionService;
	}

	/** 제재 걸기(제한·프로필 초기화·경고). 건 제재 한 줄을 돌려준다. */
	@PostMapping("/users/{userId}/sanctions")
	SanctionRow apply(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId,
			@Valid @RequestBody ApplyRequest request) {
		return sanctionService.apply(admin.getUserId(), userId, request.restrictions(), request.resets(),
				request.days(), request.reason(), request.note());
	}

	/** 회원의 제재 기록(최신순) */
	@GetMapping("/users/{userId}/sanctions")
	List<SanctionRow> ofUser(@PathVariable Long userId) {
		return sanctionService.ofUser(userId);
	}

	/** 진행 중인 제재를 지금 푼다(프로필 초기화는 되돌리지 않는다). */
	@PostMapping("/sanctions/{sanctionId}/lift")
	SanctionRow lift(@AuthenticationPrincipal LoginUser admin, @PathVariable Long sanctionId) {
		return sanctionService.lift(admin.getUserId(), sanctionId);
	}

	/** 제재 목록(최신순 100건). status: active(기본, 진행 중만)·all */
	@GetMapping("/sanctions")
	List<SanctionRow> list(@RequestParam(defaultValue = "active") String status) {
		return sanctionService.list(!"all".equalsIgnoreCase(status));
	}

	/**
	 * 값 규칙(고를 수 있는 값, 기간, 관리자 설명 200자)은 SanctionService가 확인한다. 여기서는 터무니없이 큰 요청만 막는다.
	 *
	 * @param restrictions 막을 기능(Restriction 이름, 없으면 빈 목록)
	 * @param resets 비울 프로필 항목(ProfileReset 이름, 없으면 빈 목록)
	 * @param days 기간(1·3·7·30일), null이면 해제할 때까지
	 * @param reason 사유 분류(SanctionReason 이름, 필수)
	 * @param note 관리자 설명(본인에게 보인다, 선택)
	 */
	record ApplyRequest(@Size(max = 20, message = "제한할 기능을 다시 골라 주세요.") List<String> restrictions,
			@Size(max = 20, message = "초기화할 항목을 다시 골라 주세요.") List<String> resets, Integer days,
			@Size(max = 40, message = "사유를 골라 주세요.") String reason,
			@Size(max = 1000, message = "200자 이하로 입력해 주세요.") String note) {
	}
}
