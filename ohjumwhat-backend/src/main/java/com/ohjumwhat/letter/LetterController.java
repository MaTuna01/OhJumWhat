package com.ohjumwhat.letter;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

/**
 * 쪽지 API. 보내기·답장·읽음·지우기·차단·신고는 모두 로그인한 사람 기준이다. 실시간 연결 없이 화면이 안 읽은 수를 주기적으로 받는다.
 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙(500자, 사유 100자)은 LetterService가 확인한다.
 */
@RestController
@RequestMapping("/api/letters")
class LetterController {

	private final LetterService letterService;

	LetterController(LetterService letterService) {
		this.letterService = letterService;
	}

	/** box: received(기본)·sent, before: 이 쪽지보다 오래된 것(다음 쪽) */
	@GetMapping
	LetterPageResponse list(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) String box, @RequestParam(required = false) Long before) {
		return letterService.list(loginUser.getUserId(), LetterBox.parse(box), before);
	}

	@GetMapping("/unread")
	UnreadResponse unread(@AuthenticationPrincipal LoginUser loginUser) {
		return new UnreadResponse(letterService.unreadCount(loginUser.getUserId()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LetterResponse send(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody SendRequest request) {
		return letterService.send(loginUser.getUserId(), request.organizationId(), request.recipientId(),
				request.body(), Boolean.TRUE.equals(request.anonymous()));
	}

	@PostMapping("/{letterId}/reply")
	@ResponseStatus(HttpStatus.CREATED)
	LetterResponse reply(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long letterId,
			@Valid @RequestBody ReplyRequest request) {
		return letterService.reply(loginUser.getUserId(), letterId, request.body());
	}

	@PutMapping("/{letterId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void read(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long letterId) {
		letterService.markRead(loginUser.getUserId(), letterId);
	}

	@DeleteMapping("/{letterId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long letterId) {
		letterService.delete(loginUser.getUserId(), letterId);
	}

	@PostMapping("/{letterId}/block")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void block(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long letterId) {
		letterService.block(loginUser.getUserId(), letterId);
	}

	@PostMapping("/{letterId}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long letterId,
			@Valid @RequestBody ReportRequest request) {
		letterService.report(loginUser.getUserId(), letterId, request.reason(), Boolean.TRUE.equals(request.block()));
	}

	@GetMapping("/blocks")
	List<LetterBlockResponse> blocks(@AuthenticationPrincipal LoginUser loginUser) {
		return letterService.blocks(loginUser.getUserId());
	}

	@DeleteMapping("/blocks/{blockId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unblock(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long blockId) {
		letterService.unblock(loginUser.getUserId(), blockId);
	}

	record UnreadResponse(long count) {
	}

	// 참·거짓 값은 빠져도 되게 Boolean으로 받는다(없으면 false). Jackson 3은 빠진 boolean을 오류로 본다.

	record SendRequest(@NotNull(message = "받는 사람을 골라 주세요.") Long organizationId,
			@NotNull(message = "받는 사람을 골라 주세요.") Long recipientId,
			@Size(max = 2000, message = "500자 이하로 입력해 주세요.") String body, Boolean anonymous) {
	}

	/** 답장의 익명 여부는 서버가 정한다(LetterService.reply). */
	record ReplyRequest(@Size(max = 2000, message = "500자 이하로 입력해 주세요.") String body) {
	}

	record ReportRequest(@Size(max = 400, message = "100자 이하로 입력해 주세요.") String reason, Boolean block) {
	}
}
