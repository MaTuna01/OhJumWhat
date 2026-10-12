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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.Unrestricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 투표 채팅. 보내기·고치기·지우기는 여기(REST)로 하고, 받기는 WebSocket(/api/polls/{pollId}/ws)으로 한다. */
@Tag(name = ApiTags.CHAT)
@RestController
@RequestMapping("/api/polls/{pollId}/messages")
class ChatController {

	private final ChatService chatService;

	ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	/** before(메시지 ID)보다 오래된 50개. 없으면 최신 50개 */
	@Operation(summary = "채팅 메시지 목록", description = "채팅 패널(D05-C)·채팅 시트(05-C2)를 열 때와 WebSocket이 (다시) 연결될 때 받는다. "
			+ "before보다 오래된 50개(없으면 최신 50개)를 오래된 → 최신 순으로 주고, 내가 읽은 위치(lastReadId)도 준다. "
			+ "채팅이 닫힌 뒤(마감 1시간 뒤)에도 읽을 수 있다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@GetMapping
	ChatMessagesResponse list(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "이 메시지 ID보다 오래된 것(「이전 메시지 더 보기」). 없으면 최신 50개", example = "1200")
			@RequestParam(required = false) Long before) {
		return chatService.list(pollId, loginUser.getUserId(), before);
	}

	@Operation(summary = "채팅 보내기", description = "글 메시지를 보낸다(300자, 줄바꿈 허용). 채팅은 투표가 열린 때부터 마감 1시간 뒤"
			+ "(투표 상세의 chatClosesAt)까지 쓸 수 있다. 보내면 이 메시지까지 읽은 것으로 하고, 같은 투표를 보는 사람에게 "
			+ "WebSocket으로 created를 보낸다. 한 사람 10초에 10개까지.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "채팅이 닫혔어요.", when = "마감 1시간 뒤가 지났을 때")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때(앞뒤 공백 제거)")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈이 아닌 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "300자 이하로 입력해 주세요.", when = "300자(글자 수)를 넘을 때")
	@ErrorCase(status = 429, message = "메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요.", when = "10초에 10개를 넘을 때")
	@Restricted(Restriction.CHAT)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ChatMessageResponse send(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Valid @RequestBody MessageRequest request) {
		return chatService.send(pollId, loginUser.getUserId(), request.body());
	}

	/** 내가 이 메시지까지 봤다(채팅이 닫힌 뒤에도 된다). */
	@Operation(summary = "채팅 읽음", description = "「lastReadId까지 봤다」를 알린다(안 읽은 배지·「여기부터 새 메시지」). "
			+ "위치는 뒤로 가지 않고 그 투표의 마지막 메시지를 넘지 않는다. 채팅이 닫힌 뒤에도 된다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@Unrestricted
	@PutMapping("/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Valid @RequestBody ReadRequest request) {
		chatService.markRead(pollId, loginUser.getUserId(), request.lastReadId());
	}

	@Operation(summary = "채팅 고치기", description = "내가 쓴 글 메시지만, 채팅이 열려 있을 때 고친다(사진은 고칠 수 없다). "
			+ "editedAt이 생기고 같은 투표를 보는 사람에게 WebSocket으로 updated를 보낸다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메시지를 찾을 수 없어요.", when = "이 투표의 메시지가 아니거나 지운 메시지일 때")
	@ErrorCase(status = 403, message = "내가 쓴 메시지만 고칠 수 있어요.", when = "쓴 사람이 아닐 때")
	@ErrorCase(status = 400, message = "사진은 고칠 수 없어요.", when = "사진 메시지일 때")
	@ErrorCase(status = 409, message = "채팅이 닫혔어요.", when = "마감 1시간 뒤가 지났을 때")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때(앞뒤 공백 제거)")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈이 아닌 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "300자 이하로 입력해 주세요.", when = "300자(글자 수)를 넘을 때")
	@Restricted(Restriction.CHAT)
	@PutMapping("/{messageId}")
	ChatMessageResponse edit(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메시지 ID", example = "1234") @PathVariable Long messageId,
			@Valid @RequestBody MessageRequest request) {
		return chatService.edit(pollId, messageId, loginUser.getUserId(), request.body());
	}

	@Operation(summary = "채팅 지우기", description = "내가 쓴 메시지만, 채팅이 열려 있을 때 지운다. 행은 남고 deleted가 true"
			+ "(본문·사진 없음, 「삭제된 메시지예요」)가 된다. 같은 투표를 보는 사람에게 WebSocket으로 deleted를 보낸다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메시지를 찾을 수 없어요.", when = "이 투표의 메시지가 아니거나 이미 지운 메시지일 때")
	@ErrorCase(status = 403, message = "내가 쓴 메시지만 지울 수 있어요.", when = "쓴 사람이 아닐 때")
	@ErrorCase(status = 409, message = "채팅이 닫혔어요.", when = "마감 1시간 뒤가 지났을 때")
	@Unrestricted
	@DeleteMapping("/{messageId}")
	ChatMessageResponse delete(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메시지 ID", example = "1234") @PathVariable Long messageId) {
		return chatService.delete(pollId, messageId, loginUser.getUserId());
	}

	/** 본문 길이·문자는 서비스에서 글자(코드 포인트) 수로 확인한다. 여기서는 터무니없이 긴 요청만 막는다. */
	@Schema(name = "ChatMessageRequest")
	record MessageRequest(
			@Schema(description = "메시지 본문(앞뒤 공백 제거, 줄바꿈 허용, 300자)", example = "저 5분 늦어요!",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 2000, message = "300자 이하로 입력해 주세요.") String body) {
	}

	/**
	 * 읽은 위치.
	 *
	 * @param lastReadId 마지막으로 본 메시지 ID(0이면 아무것도 바꾸지 않는다)
	 */
	@Schema(name = "ChatReadRequest")
	record ReadRequest(
			@Schema(description = "마지막으로 본 메시지 ID(0 이상, 0이면 아무것도 바꾸지 않는다)", example = "1234",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull(message = "읽은 위치가 올바르지 않아요.")
			@Min(value = 0, message = "읽은 위치가 올바르지 않아요.")
			Long lastReadId) {
	}
}
