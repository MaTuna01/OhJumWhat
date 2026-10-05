package com.ohjumwhat.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/** 투표 채팅. 보내기·고치기·지우기는 여기(REST)로 하고, 받기는 WebSocket(/api/polls/{pollId}/ws)으로 한다. */
@RestController
@RequestMapping("/api/polls/{pollId}/messages")
class ChatController {

	private final ChatService chatService;

	ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	/** before(메시지 ID)보다 오래된 50개. 없으면 최신 50개 */
	@GetMapping
	ChatMessagesResponse list(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@RequestParam(required = false) Long before) {
		return chatService.list(pollId, loginUser.getUserId(), before);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ChatMessageResponse send(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@Valid @RequestBody MessageRequest request) {
		return chatService.send(pollId, loginUser.getUserId(), request.body());
	}

	/** 내가 이 메시지까지 봤다(채팅이 닫힌 뒤에도 된다). */
	@PutMapping("/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@Valid @RequestBody ReadRequest request) {
		chatService.markRead(pollId, loginUser.getUserId(), request.lastReadId());
	}

	@PutMapping("/{messageId}")
	ChatMessageResponse edit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long messageId, @Valid @RequestBody MessageRequest request) {
		return chatService.edit(pollId, messageId, loginUser.getUserId(), request.body());
	}

	@DeleteMapping("/{messageId}")
	ChatMessageResponse delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long messageId) {
		return chatService.delete(pollId, messageId, loginUser.getUserId());
	}

	/** 본문 길이·문자는 서비스에서 글자(코드 포인트) 수로 확인한다. 여기서는 터무니없이 긴 요청만 막는다. */
	record MessageRequest(@Size(max = 2000, message = "300자 이하로 입력해 주세요.") String body) {
	}

	/**
	 * 읽은 위치.
	 *
	 * @param lastReadId 마지막으로 본 메시지 ID(0이면 아무것도 바꾸지 않는다)
	 */
	record ReadRequest(
			@NotNull(message = "읽은 위치가 올바르지 않아요.")
			@Min(value = 0, message = "읽은 위치가 올바르지 않아요.")
			Long lastReadId) {
	}
}
