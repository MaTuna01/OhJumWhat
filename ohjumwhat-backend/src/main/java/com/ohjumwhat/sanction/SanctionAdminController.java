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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 관리자 콘솔의 제재. /api/admin/**는 관리자만 호출할 수 있다(SecurityConfig). */
@Tag(name = ApiTags.ADMIN_SANCTION)
@RestController
@RequestMapping("/api/admin")
class SanctionAdminController {

	private final SanctionService sanctionService;

	SanctionAdminController(SanctionService sanctionService) {
		this.sanctionService = sanctionService;
	}

	/** 제재 걸기(제한·프로필 초기화·경고). 건 제재 한 줄을 돌려준다. */
	@Operation(summary = "제재하기", description = "회원에게 제재를 건다(A03-M5). 제한(restrictions)·프로필 초기화(resets)·사유를 한 번에 저장하고, "
			+ "제한도 초기화도 없으면 경고다. SUSPEND(활동 정지)를 고르면 다른 제한은 버리고 활동 정지만 남긴다. 지금 비어 있는 프로필 항목의 초기화는 "
			+ "하지 않고 기록에서도 뺀다. 그 사람에 대한 처리 전 사람 신고는 모두 「조치함」으로 처리되고, 본인과 신고한 사람에게 커밋 뒤 푸시로 알린다. "
			+ "확인 순서는 회원(404) → 나·관리자(409) → 값(400)이다. 응답은 건 제재 한 줄이다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@ErrorCase(status = 409, message = "자기 자신은 제재할 수 없어요.", when = "대상이 나일 때")
	@ErrorCase(status = 409, message = "관리자는 제재할 수 없어요.", when = "대상이 관리자일 때")
	@ErrorCase(status = 400, message = "제한할 기능을 다시 골라 주세요.", when = "restrictions에 Restriction이 아닌 값이 있을 때")
	@ErrorCase(status = 400, message = "초기화할 항목을 다시 골라 주세요.", when = "resets에 ProfileReset이 아닌 값이 있을 때")
	@ErrorCase(status = 400, message = "사유를 골라 주세요.", when = "reason이 없거나 SanctionReason이 아닐 때")
	@ErrorCase(status = 400, message = "기간은 1일·3일·7일·30일 중에서 골라 주세요.", when = "days가 1·3·7·30이 아닐 때")
	@ErrorCase(status = 400, message = "제한을 고르지 않으면 기간을 정할 수 없어요.", when = "restrictions 없이 days를 보냈을 때")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "note에 줄바꿈 말고 제어 문자가 있을 때")
	@PostMapping("/users/{userId}/sanctions")
	SanctionRow apply(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "제재할 회원 ID", example = "7") @PathVariable Long userId,
			@Valid @RequestBody ApplyRequest request) {
		return sanctionService.apply(admin.getUserId(), userId, request.restrictions(), request.resets(),
				request.days(), request.reason(), request.note());
	}

	/** 회원의 제재 기록(최신순) */
	@Operation(summary = "회원의 제재 기록", description = "회원 상세(A03b)의 「이용 제한」 카드 이력. 최신순 100건이다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@GetMapping("/users/{userId}/sanctions")
	List<SanctionRow> ofUser(@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId) {
		return sanctionService.ofUser(userId);
	}

	/** 진행 중인 제재를 지금 푼다(프로필 초기화는 되돌리지 않는다). */
	@Operation(summary = "제재 해제", description = "진행 중(ACTIVE)인 제재를 지금 푼다(A03-M6). 함께 한 프로필 초기화는 되돌리지 않고, 본인에게 따로 "
			+ "알리지 않는다(아직 안내를 보지 않았으면 안내 창에 「해제됨」으로 보인다). 응답은 해제한 제재 한 줄이다.")
	@ErrorCase(status = 404, message = "제재를 찾을 수 없어요.", when = "제재가 없을 때")
	@ErrorCase(status = 409, message = "이미 끝났거나 해제된 제재예요.", when = "status가 ACTIVE가 아닐 때(끝남·해제됨·경고·초기화만)")
	@PostMapping("/sanctions/{sanctionId}/lift")
	SanctionRow lift(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "제재 ID", example = "3") @PathVariable Long sanctionId) {
		return sanctionService.lift(admin.getUserId(), sanctionId);
	}

	/** 제재 목록(최신순 100건). status: active(기본, 진행 중만)·all */
	@Operation(summary = "제재 목록", description = "「제재」 탭(A10). 최신순 100건이다.")
	@GetMapping("/sanctions")
	List<SanctionRow> list(
			@Parameter(description = "active(기본, 진행 중만)·all(전체). 그 밖의 값은 active로 본다", example = "active")
			@RequestParam(defaultValue = "active") String status) {
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
	@Schema(name = "SanctionApplyRequest", description = "제재하기. 제한·초기화가 모두 비면 경고다")
	record ApplyRequest(
			@Schema(description = "막을 기능(Restriction 이름, 대소문자 구분). 비우거나 빼면 제한 없음. SUSPEND가 있으면 SUSPEND만 남긴다",
					example = "[\"CHAT\", \"LETTER\"]", nullable = true)
			@Size(max = 20, message = "제한할 기능을 다시 골라 주세요.") List<String> restrictions,
			@Schema(description = "비울 프로필 항목(ProfileReset 이름, 대소문자 구분). 비우거나 빼면 초기화 없음. 되돌리지 않는다",
					example = "[\"NICKNAME\"]", nullable = true)
			@Size(max = 20, message = "초기화할 항목을 다시 골라 주세요.") List<String> resets,
			@Schema(description = "제한 기간(1·3·7·30일). null이면 해제할 때까지. 제한이 없으면 null이어야 한다", example = "7", nullable = true)
			Integer days,
			@Schema(description = "사유 분류(SanctionReason 이름, 본인에게 보인다)", example = "ABUSE", requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 40, message = "사유를 골라 주세요.") String reason,
			@Schema(description = "관리자 설명(선택, 줄바꿈 허용 200자). 본인에게 보인다(신고한 사람에게는 보이지 않는다)",
					example = "채팅에서 반복적으로 욕설을 했어요.", nullable = true)
			@Size(max = 1000, message = "200자 이하로 입력해 주세요.") String note) {
	}
}
