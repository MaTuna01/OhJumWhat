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
 * 쪽지 API. 보내기·답장·읽음·지우기·차단·신고는 모두 로그인한 사람 기준이다. 실시간 연결 없이 화면이 안 읽은 수를 주기적으로 받는다.
 * 여기의 크기 제한은 큰 요청을 막는 방어선이고, 실제 규칙(500자, 사유 100자)은 LetterService가 확인한다.
 */
@Tag(name = ApiTags.LETTER)
@RestController
@RequestMapping("/api/letters")
class LetterController {

	private final LetterService letterService;

	LetterController(LetterService letterService) {
		this.letterService = letterService;
	}

	/** box: received(기본)·sent, before: 이 쪽지보다 오래된 것(다음 쪽) */
	@Operation(summary = "쪽지함", description = "쪽지함(D10 받은 쪽지, D10b 보낸 쪽지) 한 쪽. 최신순 20통이고, hasMore면 마지막 쪽지의 id를 "
			+ "before로 보내 다음 쪽을 받는다. 내가 지운 쪽지와 차단으로 숨긴 받은 쪽지는 빠진다.")
	@ErrorCase(status = 400, message = "쪽지함은 received나 sent예요.", when = "box가 received·sent가 아닐 때")
	@GetMapping
	LetterPageResponse list(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "쪽지함: received(받은 쪽지, 기본)·sent(보낸 쪽지). 대소문자 무시", example = "received")
			@RequestParam(required = false) String box,
			@Parameter(description = "이 쪽지 ID보다 오래된 것(다음 쪽). 비우면 처음부터", example = "120")
			@RequestParam(required = false) Long before) {
		return letterService.list(loginUser.getUserId(), LetterBox.parse(box), before);
	}

	@Operation(summary = "안 읽은 쪽지 수", description = "상단 바 봉투의 빨간 배지(99+). 화면이 30초마다·창으로 돌아올 때 받는다. "
			+ "차단으로 숨긴 쪽지는 세지 않는다.")
	@GetMapping("/unread")
	UnreadResponse unread(@AuthenticationPrincipal LoginUser loginUser) {
		return new UnreadResponse(letterService.unreadCount(loginUser.getUserId()));
	}

	@Operation(summary = "쪽지 보내기", description = "같은 조직 멤버에게 쪽지 한 통을 보낸다(10-M1·10-M1b). anonymous면 받은 사람의 응답에 보낸 사람의 "
			+ "id·이름·사진이 없다. 받는 사람이 나를 차단해 두었으면 받는 사람 쪽지함에는 들어가지 않지만 응답은 똑같이 201이다"
			+ "(차단됐는지 알리지 않는다). 응답은 보낸 쪽지(보낸 쪽지함 기준)다.")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈 말고 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "나에게는 쪽지를 보낼 수 없어요.", when = "받는 사람이 나일 때")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 내가 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "받는 사람을 찾을 수 없어요.", when = "받는 사람이 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 429, message = "쪽지를 너무 많이 보냈어요. 잠시 후 다시 보내 주세요.", when = "10분에 10통(답장 포함)을 넘었을 때")
	@Restricted(Restriction.LETTER)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LetterResponse send(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody SendRequest request) {
		return letterService.send(loginUser.getUserId(), request.organizationId(), request.recipientId(),
				request.body(), Boolean.TRUE.equals(request.anonymous()));
	}

	@Operation(summary = "답장", description = "받은 쪽지에 답장한다(10-M1c). 답장은 원래 보낸 사람에게 간다. 익명 여부는 서버가 정한다: "
			+ "받은 쪽지의 replyAnonymous가 true일 때만(내가 익명으로 시작한 대화에 온 답장) 익명이고, 그 밖에는 실명이다. "
			+ "익명 쪽지에 쓴 답장은 보낸 쪽지함에서 받는 사람이 「익명」으로 보인다. 응답은 보낸 답장(보낸 쪽지함 기준)이다.")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈 말고 제어 문자가 있을 때")
	@ErrorCase(status = 404, message = "쪽지를 찾을 수 없어요.", when = "내가 받은 쪽지가 아니거나 지웠거나 차단으로 숨겼을 때")
	@ErrorCase(status = 409, message = "답장할 수 없는 쪽지예요.",
			when = "조직이 없어졌거나, 내가 그 조직 멤버가 아니거나, 보낸 사람이 탈퇴했을 때(이유는 나누지 않는다)")
	@ErrorCase(status = 429, message = "쪽지를 너무 많이 보냈어요. 잠시 후 다시 보내 주세요.", when = "10분에 10통(답장 포함)을 넘었을 때")
	@Restricted(Restriction.LETTER)
	@PostMapping("/{letterId}/reply")
	@ResponseStatus(HttpStatus.CREATED)
	LetterResponse reply(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "답장할 받은 쪽지 ID", example = "120") @PathVariable Long letterId,
			@Valid @RequestBody ReplyRequest request) {
		return letterService.reply(loginUser.getUserId(), letterId, request.body());
	}

	@Operation(summary = "쪽지 읽음", description = "받은 쪽지를 열면(10-M2) 처음 읽은 시각을 남긴다. 이미 읽었으면 그대로다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "쪽지를 찾을 수 없어요.", when = "내가 받은 쪽지가 아니거나 지웠거나 차단으로 숨겼을 때")
	@Unrestricted
	@PutMapping("/{letterId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void read(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "받은 쪽지 ID", example = "120") @PathVariable Long letterId) {
		letterService.markRead(loginUser.getUserId(), letterId);
	}

	@Operation(summary = "쪽지 지우기", description = "받은 쪽지·보낸 쪽지를 **내 쪽지함에서만** 지운다(10-M2·10-M2c). 상대 쪽지함에는 남는다. "
			+ "성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "쪽지를 찾을 수 없어요.", when = "내 쪽지함에 없는 쪽지일 때(이미 지운 쪽지 포함)")
	@Unrestricted
	@DeleteMapping("/{letterId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "쪽지 ID", example = "120") @PathVariable Long letterId) {
		letterService.delete(loginUser.getUserId(), letterId);
	}

	@Operation(summary = "보낸 사람 차단", description = "받은 쪽지의 보낸 사람을 차단한다(10-M5). 실명 쪽지는 **사람 단위**로 그 사람의 실명 쪽지를 "
			+ "모두 숨기고, 익명 쪽지는 **그 쪽지 한 통**만 숨긴다(같은 사람의 다른 익명 쪽지는 그대로 보인다). 차단이 하나라도 걸린 사람이 "
			+ "새로 보내는 익명 쪽지는 받지 않는다. 다시 눌러도 그대로이고, 탈퇴한 사람의 실명 쪽지는 아무것도 하지 않는다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "쪽지를 찾을 수 없어요.", when = "내가 받은 쪽지가 아니거나 지웠을 때(차단으로 숨긴 쪽지는 찾는다)")
	@ErrorCase(status = 409, message = "다른 사람의 변경과 겹쳤어요. 다시 시도해 주세요.", when = "같은 차단을 동시에 보냈을 때")
	@Unrestricted
	@PostMapping("/{letterId}/block")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void block(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "받은 쪽지 ID", example = "120") @PathVariable Long letterId) {
		letterService.block(loginUser.getUserId(), letterId);
	}

	@Operation(summary = "쪽지 신고", description = "받은 쪽지를 신고한다(10-M3). 한 쪽지는 한 번만 신고되고, 다시 보내도 그대로 204다. "
			+ "block이면 「보낸 사람 차단」도 함께 한다. 관리자는 신고된 쪽지만 볼 수 있다(익명 쪽지도 관리자에게는 실제 보낸 사람이 보인다).")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "사유에 제어 문자(줄바꿈 포함)가 있을 때")
	@ErrorCase(status = 404, message = "쪽지를 찾을 수 없어요.", when = "내가 받은 쪽지가 아니거나 지웠을 때(차단으로 숨긴 쪽지는 찾는다)")
	@ErrorCase(status = 409, message = "다른 사람의 변경과 겹쳤어요. 다시 시도해 주세요.", when = "같은 신고를 동시에 보냈을 때")
	@Unrestricted
	@PostMapping("/{letterId}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "받은 쪽지 ID", example = "120") @PathVariable Long letterId,
			@Valid @RequestBody ReportRequest request) {
		letterService.report(loginUser.getUserId(), letterId, request.reason(), Boolean.TRUE.equals(request.block()));
	}

	@Operation(summary = "차단 목록", description = "차단한 사람 관리(10-M4). 실명 차단은 사람을, 익명 차단은 이름 없이 차단한 쪽지의 첫 줄을 준다.")
	@GetMapping("/blocks")
	List<LetterBlockResponse> blocks(@AuthenticationPrincipal LoginUser loginUser) {
		return letterService.blocks(loginUser.getUserId());
	}

	@Operation(summary = "차단 풀기", description = "차단을 푼다(10-M4). 차단하기 전에 받은 쪽지만 다시 보이고, 차단 중에 받지 않은 쪽지는 돌아오지 않는다. "
			+ "성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "차단을 찾을 수 없어요.", when = "내 차단이 아닐 때")
	@Unrestricted
	@DeleteMapping("/blocks/{blockId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unblock(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "차단 ID(차단 목록의 id)", example = "5") @PathVariable Long blockId) {
		letterService.unblock(loginUser.getUserId(), blockId);
	}

	@Schema(name = "LetterUnreadResponse", description = "안 읽은 받은 쪽지 수")
	record UnreadResponse(@Schema(description = "안 읽은 받은 쪽지 수(차단으로 숨긴 쪽지 제외)", example = "3") long count) {
	}

	// 참·거짓 값은 빠져도 되게 Boolean으로 받는다(없으면 false). Jackson 3은 빠진 boolean을 오류로 본다.

	@Schema(name = "LetterSendRequest", description = "쪽지 보내기")
	record SendRequest(
			@Schema(description = "어느 조직에서 보내는지(보낸 사람·받는 사람 모두 그 조직 멤버여야 한다)", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull(message = "받는 사람을 골라 주세요.") Long organizationId,
			@Schema(description = "받는 사람 회원 ID(나는 안 된다)", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull(message = "받는 사람을 골라 주세요.") Long recipientId,
			@Schema(description = "본문(앞뒤 공백 제거, 줄바꿈 허용, 500자)", example = "오늘 점심 같이 가요!",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 2000, message = "500자 이하로 입력해 주세요.") String body,
			@Schema(description = "익명으로 보낼지(빠지면 false). 익명이면 받은 사람에게 보낸 사람의 id·이름·사진이 가지 않는다",
					example = "false")
			Boolean anonymous) {
	}

	/** 답장의 익명 여부는 서버가 정한다(LetterService.reply). */
	@Schema(name = "LetterReplyRequest", description = "답장. 익명 여부는 서버가 정하므로 보내지 않는다(받은 쪽지의 replyAnonymous)")
	record ReplyRequest(
			@Schema(description = "본문(앞뒤 공백 제거, 줄바꿈 허용, 500자)", example = "좋아요, 12시에 봬요.",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 2000, message = "500자 이하로 입력해 주세요.") String body) {
	}

	@Schema(name = "LetterReportRequest", description = "쪽지 신고")
	record ReportRequest(
			@Schema(description = "신고 사유(선택, 한 줄 100자. 줄바꿈은 받지 않으므로 화면이 공백으로 합쳐 보낸다)", example = "욕설이 있어요",
					nullable = true)
			@Size(max = 400, message = "100자 이하로 입력해 주세요.") String reason,
			@Schema(description = "보낸 사람도 함께 차단할지(빠지면 false)", example = "true") Boolean block) {
	}
}
