package com.ohjumwhat.menu;

import java.util.List;

import jakarta.validation.Valid;
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

/** 메뉴 댓글. 쓰기 API는 그 메뉴의 최신 댓글 목록을 돌려준다. */
@Tag(name = ApiTags.COMMENT)
@RestController
@RequestMapping("/api/polls/{pollId}/options/{optionId}/comments")
class MenuCommentController {

	private final MenuCommentService menuCommentService;

	MenuCommentController(MenuCommentService menuCommentService) {
		this.menuCommentService = menuCommentService;
	}

	@Operation(summary = "메뉴 댓글 목록", description = "메뉴 카드 아래 「💬 댓글 N」을 펼칠 때(05-K) 받는다. 오래된 순. "
			+ "마감된 투표의 댓글도 읽을 수 있다(05b-K, 읽기 전용).")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "이 투표의 메뉴가 아닐 때")
	@GetMapping
	List<MenuCommentResponse> list(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId) {
		return menuCommentService.list(pollId, optionId, loginUser.getUserId());
	}

	@Operation(summary = "메뉴 댓글 쓰기", description = "진행 중인 투표에서만 쓴다. 한 줄 200자. 응답은 그 메뉴의 최신 댓글 목록이다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "이 투표의 메뉴가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때(앞뒤 공백 제거)")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈 등 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "200자 이하로 입력해 주세요.", when = "200자(글자 수)를 넘을 때")
	@Restricted(Restriction.POLL)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	List<MenuCommentResponse> add(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId, @Valid @RequestBody CommentRequest request) {
		return menuCommentService.add(pollId, optionId, loginUser.getUserId(), request.body());
	}

	@Operation(summary = "메뉴 댓글 고치기", description = "쓴 사람만, 진행 중인 투표에서만 고친다. 고치면 edited가 true(「수정됨」)가 된다. "
			+ "응답은 그 메뉴의 최신 댓글 목록이다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "이 투표의 메뉴가 아닐 때")
	@ErrorCase(status = 404, message = "댓글을 찾을 수 없어요.", when = "이 메뉴의 댓글이 아닐 때")
	@ErrorCase(status = 403, message = "내가 쓴 댓글만 고칠 수 있어요.", when = "쓴 사람이 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 400, message = "내용을 입력해 주세요.", when = "본문이 비었을 때(앞뒤 공백 제거)")
	@ErrorCase(status = 400, message = "쓸 수 없는 문자가 있어요.", when = "줄바꿈 등 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "200자 이하로 입력해 주세요.", when = "200자(글자 수)를 넘을 때")
	@Restricted(Restriction.POLL)
	@PutMapping("/{commentId}")
	List<MenuCommentResponse> edit(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId,
			@Parameter(description = "댓글 ID", example = "500") @PathVariable Long commentId, @Valid @RequestBody CommentRequest request) {
		return menuCommentService.edit(pollId, optionId, commentId, loginUser.getUserId(), request.body());
	}

	@Operation(summary = "메뉴 댓글 지우기", description = "쓴 사람만, 진행 중인 투표에서만 지운다(행을 지운다). 응답은 그 메뉴의 남은 댓글 목록이다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "이 투표의 메뉴가 아닐 때")
	@ErrorCase(status = 404, message = "댓글을 찾을 수 없어요.", when = "이 메뉴의 댓글이 아닐 때")
	@ErrorCase(status = 403, message = "내가 쓴 댓글만 지울 수 있어요.", when = "쓴 사람이 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@Unrestricted
	@DeleteMapping("/{commentId}")
	List<MenuCommentResponse> delete(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId,
			@Parameter(description = "댓글 ID", example = "500") @PathVariable Long commentId) {
		return menuCommentService.delete(pollId, optionId, commentId, loginUser.getUserId());
	}

	/** 본문 길이·문자는 서비스에서 글자(코드 포인트) 수로 확인한다. 여기서는 터무니없이 긴 요청만 막는다. */
	@Schema(name = "MenuCommentRequest")
	record CommentRequest(
			@Schema(description = "댓글 본문(앞뒤 공백 제거, 한 줄, 200자)", example = "여기 국물 진짜 맛있어요",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 1000, message = "200자 이하로 입력해 주세요.") String body) {
	}
}
