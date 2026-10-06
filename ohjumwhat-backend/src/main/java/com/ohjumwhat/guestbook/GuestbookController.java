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

/**
 * 방명록 API. 모두 로그인한 사람 기준이다. 실시간 연결 없이 화면이 알림 요약을 주기적으로 받는다.
 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙(100자, 사유 100자)은 GuestbookService가 확인한다.
 */
@RestController
@RequestMapping("/api/guestbook")
class GuestbookController {

	private final GuestbookService guestbookService;

	GuestbookController(GuestbookService guestbookService) {
		this.guestbookService = guestbookService;
	}

	/** 그 사람의 방명록(최신순 10개씩). page는 0부터 */
	@GetMapping("/users/{ownerId}")
	GuestbookPageResponse list(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long ownerId,
			@RequestParam(defaultValue = "0") int page) {
		return guestbookService.list(loginUser.getUserId(), ownerId, page);
	}

	/** 그 사람의 방명록에 쓴다. 쓴 사람이 보는 0쪽을 돌려준다. */
	@PostMapping("/users/{ownerId}")
	@ResponseStatus(HttpStatus.CREATED)
	GuestbookPageResponse write(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long ownerId,
			@Valid @RequestBody WriteRequest request) {
		return guestbookService.write(loginUser.getUserId(), ownerId, request.body());
	}

	@DeleteMapping("/entries/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long entryId) {
		guestbookService.delete(loginUser.getUserId(), entryId);
	}

	/** 내 방명록의 글을 신고한다. 사유는 선택이다(본문이 없어도 된다). */
	@PostMapping("/entries/{entryId}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long entryId,
			@Valid @RequestBody(required = false) ReportRequest request) {
		guestbookService.report(loginUser.getUserId(), entryId, request == null ? null : request.reason());
	}

	/** 알림 요약: 내 방명록의 새 글 수와 확인하지 않은 경고(제한된 내 글) */
	@GetMapping("/alerts")
	GuestbookAlertsResponse alerts(@AuthenticationPrincipal LoginUser loginUser) {
		return guestbookService.alerts(loginUser.getUserId());
	}

	/** 내 방명록을 until(화면에 보인 가장 최근 글의 시각)까지 봤다. */
	@PostMapping("/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody UntilRequest request) {
		guestbookService.markSeen(loginUser.getUserId(), request.until());
	}

	/** 경고를 until(보여준 경고 중 가장 최근 제한 시각)까지 확인했다. */
	@PostMapping("/warnings/ack")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void ackWarnings(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody UntilRequest request) {
		guestbookService.ackWarnings(loginUser.getUserId(), request.until());
	}

	record WriteRequest(@Size(max = 400, message = "100자 이하로 입력해 주세요.") String body) {
	}

	record ReportRequest(@Size(max = 400, message = "100자 이하로 입력해 주세요.") String reason) {
	}

	record UntilRequest(@NotNull(message = "시각이 올바르지 않아요.") Instant until) {
	}
}
