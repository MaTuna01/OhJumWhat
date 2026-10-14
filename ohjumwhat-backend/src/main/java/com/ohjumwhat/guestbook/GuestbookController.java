package com.ohjumwhat.guestbook;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiLimit;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.Unrestricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 방명록 API. 모두 로그인한 사람 기준이다. 실시간 연결 없이 화면이 알림 요약을 주기적으로 받는다.
 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙(100자, 사유 100자)은 GuestbookService가 확인한다.
 */
@Tag(name = ApiTags.GUESTBOOK)
@RestController
@RequestMapping("/api/guestbook")
class GuestbookController {

	private final GuestbookService guestbookService;

	GuestbookController(GuestbookService guestbookService) {
		this.guestbookService = guestbookService;
	}

	/** 그 사람의 방명록(최신순 10개씩). page는 0부터 */
	@Operation(summary = "방명록", description = "그 사람의 방명록 한 쪽(최신순 10개). 멤버 프로필 모달의 「방명록」 탭(07-P4)과 마이페이지 "
			+ "방명록(03-N5)에서 쓴다. 주인 본인과, 주인과 조직을 하나라도 같이 쓰는 사람만 볼 수 있다. 지운 글은 빠지고 제한된 글은 "
			+ "본문 없이 들어간다. 쪽이 끝을 넘으면 entries만 빈다.")
	@ErrorCase(status = 400, message = "페이지 번호가 올바르지 않아요.", when = "page가 0보다 작거나 10000보다 클 때")
	@ErrorCase(status = 404, message = "방명록을 찾을 수 없어요.", when = "주인이 없거나 주인과 같은 조직이 아닐 때")
	@GetMapping("/users/{ownerId}")
	GuestbookPageResponse list(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "방명록 주인 회원 ID(내 방명록이면 내 ID)", example = "7") @PathVariable Long ownerId,
			@Parameter(description = "페이지(0부터)", example = "0") @RequestParam(defaultValue = "0") int page) {
		return guestbookService.list(loginUser.getUserId(), ownerId, page);
	}

	/** 그 사람의 방명록에 쓴다. 쓴 사람이 보는 0쪽을 돌려준다. */
	@Operation(summary = "방명록 쓰기", description = "주인과 같은 조직인 사람이 한 줄(100자, 줄바꿈 불가)을 남긴다(07-P4). 실명(별명)으로만 쓰고, "
			+ "고치기·댓글은 없다. 한 사람이 모든 방명록을 합쳐 5초에 한 번이고(글을 확인한 뒤에 센다), 화면은 성공·429 뒤 5초 "
			+ "카운트다운(07-P4c)을 보여준다. 응답은 201과 쓴 사람이 보는 0쪽이다.")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈 등 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "내 방명록에는 글을 남길 수 없어요.", when = "주인이 나일 때")
	@ErrorCase(status = 404, message = "방명록을 찾을 수 없어요.", when = "주인이 없거나 주인과 같은 조직이 아닐 때")
	@ErrorCase(status = 429, message = "방명록은 5초에 한 번 남길 수 있어요. 잠시 후 다시 남겨 주세요.", when = "5초 안에 다시 썼을 때")
	@Restricted(Restriction.GUESTBOOK)
	@PostMapping("/users/{ownerId}")
	@ResponseStatus(HttpStatus.CREATED)
	GuestbookPageResponse write(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "방명록 주인 회원 ID", example = "7") @PathVariable Long ownerId,
			@Valid @RequestBody WriteRequest request) {
		return guestbookService.write(loginUser.getUserId(), ownerId, request.body());
	}

	@Operation(summary = "방명록 글 지우기", description = "쓴 사람(지금 같은 조직이 아니어도)이나 방명록 주인이 지운다(07-M3). 관리자가 제한한 글은 "
			+ "주인만 지울 수 있다. 지운 글은 목록·개수에서 빠진다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "방명록 글을 찾을 수 없어요.", when = "글이 없거나 이미 지웠거나, 볼 수 없는 방명록의 글일 때")
	@ErrorCase(status = 403, message = "내가 쓴 글이나 내 방명록의 글만 지울 수 있어요.", when = "볼 수는 있지만 쓴 사람도 주인도 아닐 때")
	@ErrorCase(status = 403, message = "관리자가 제한한 글은 방명록 주인만 지울 수 있어요.", when = "제한된 글을 쓴 사람이 지울 때")
	@Unrestricted
	@DeleteMapping("/entries/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "방명록 글 ID", example = "30") @PathVariable Long entryId) {
		guestbookService.delete(loginUser.getUserId(), entryId);
	}

	/** 내 방명록의 글을 신고한다. 사유는 선택이다(본문이 없어도 된다). */
	@Operation(summary = "방명록 글 신고", description = "방명록 주인이 내 방명록의 글을 신고한다(07-M2). 글 하나는 한 번만 신고되고, 이미 신고했거나 "
			+ "제한된 글이면 그대로 204다. 본문은 없어도 된다. 관리자가 「글 제한」으로 처리하면 모두에게 「관리자에 의해 제한된 게시글입니다」로 "
			+ "보인다.")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "사유에 제어 문자(줄바꿈 포함)가 있을 때")
	@ErrorCase(status = 404, message = "방명록 글을 찾을 수 없어요.", when = "글이 없거나 지웠거나, 볼 수 없는 방명록의 글일 때")
	@ErrorCase(status = 403, message = "내 방명록의 글만 신고할 수 있어요.", when = "주인이 아닐 때")
	@ErrorCase(status = 409, message = "다른 사람의 변경과 겹쳤어요. 다시 시도해 주세요.", when = "같은 글의 신고가 동시에 들어왔을 때")
	@Unrestricted
	@PostMapping("/entries/{entryId}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "방명록 글 ID", example = "30") @PathVariable Long entryId,
			@Valid @RequestBody(required = false) ReportRequest request) {
		guestbookService.report(loginUser.getUserId(), entryId, request == null ? null : request.reason());
	}

	/** 알림 요약: 내 방명록의 새 글 수와 확인하지 않은 경고(제한된 내 글) */
	@Operation(summary = "방명록 알림", description = "상단 바 프로필 사진의 빨간 점·프로필 메뉴 「새 방명록 N」(03-N5c)과 글 제한 경고 창(07-M4)에 쓴다. "
			+ "내 방명록의 새 글 수와, 아직 확인하지 않은 제한된 내 글을 준다.")
	@GetMapping("/alerts")
	GuestbookAlertsResponse alerts(@AuthenticationPrincipal LoginUser loginUser) {
		return guestbookService.alerts(loginUser.getUserId());
	}

	/** 내 방명록을 until(화면에 보인 가장 최근 글의 시각)까지 봤다. */
	@Operation(summary = "내 방명록 봤음", description = "내 방명록 0쪽이 보이면 화면에 보인 가장 최근 글의 createdAt을 until로 보낸다. "
			+ "그 시각까지의 글은 새 글이 아니다. 지금보다 뒤면 지금으로 자르고, 뒤로 가지 않는다. 성공하면 204(본문 없음).")
	@Unrestricted
	@PostMapping("/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody UntilRequest request) {
		guestbookService.markSeen(loginUser.getUserId(), request.until());
	}

	/** 경고를 until(보여준 경고 중 가장 최근 제한 시각)까지 확인했다. */
	@Operation(summary = "글 제한 경고 확인", description = "글 제한 경고 창(07-M4)을 닫으면 보여준 경고 중 가장 최근 restrictedAt을 until로 보낸다. "
			+ "「내 방명록 봤음」과 같은 규칙이다. 성공하면 204(본문 없음).")
	@Unrestricted
	@PostMapping("/warnings/ack")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void ackWarnings(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody UntilRequest request) {
		guestbookService.ackWarnings(loginUser.getUserId(), request.until());
	}

	@Schema(name = "GuestbookWriteRequest", description = "방명록 쓰기")
	record WriteRequest(
			@Schema(description = "본문(앞뒤 공백 제거, 한 줄 100자, 줄바꿈 불가)", example = "지난번 추천해 준 김치찌개 맛있었어요!",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@ApiLimit(100)
			@Size(max = 400, message = "100자 이하로 입력해 주세요.") String body) {
	}

	@Schema(name = "GuestbookReportRequest", description = "방명록 글 신고(본문 자체를 보내지 않아도 된다)")
	record ReportRequest(
			@Schema(description = "신고 사유(선택, 한 줄 100자)", example = "광고 글이에요", nullable = true)
			@ApiLimit(100)
			@Size(max = 400, message = "100자 이하로 입력해 주세요.") String reason) {
	}

	@Schema(name = "GuestbookUntilRequest", description = "여기까지 봤다는 시각")
	record UntilRequest(
			@Schema(description = "본 것 중 가장 최근 시각(UTC). 봤음은 글의 createdAt, 경고 확인은 restrictedAt",
					example = "2026-10-12T02:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull(message = "시각이 올바르지 않아요.") Instant until) {
	}
}
