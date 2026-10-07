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
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.Unrestricted;

/** 메뉴 댓글. 쓰기 API는 그 메뉴의 최신 댓글 목록을 돌려준다. */
@RestController
@RequestMapping("/api/polls/{pollId}/options/{optionId}/comments")
class MenuCommentController {

	private final MenuCommentService menuCommentService;

	MenuCommentController(MenuCommentService menuCommentService) {
		this.menuCommentService = menuCommentService;
	}

	@GetMapping
	List<MenuCommentResponse> list(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId) {
		return menuCommentService.list(pollId, optionId, loginUser.getUserId());
	}

	@Restricted(Restriction.POLL)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	List<MenuCommentResponse> add(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId, @Valid @RequestBody CommentRequest request) {
		return menuCommentService.add(pollId, optionId, loginUser.getUserId(), request.body());
	}

	@Restricted(Restriction.POLL)
	@PutMapping("/{commentId}")
	List<MenuCommentResponse> edit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId, @PathVariable Long commentId, @Valid @RequestBody CommentRequest request) {
		return menuCommentService.edit(pollId, optionId, commentId, loginUser.getUserId(), request.body());
	}

	@Unrestricted
	@DeleteMapping("/{commentId}")
	List<MenuCommentResponse> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId, @PathVariable Long commentId) {
		return menuCommentService.delete(pollId, optionId, commentId, loginUser.getUserId());
	}

	/** 본문 길이·문자는 서비스에서 글자(코드 포인트) 수로 확인한다. 여기서는 터무니없이 긴 요청만 막는다. */
	record CommentRequest(@Size(max = 1000, message = "200자 이하로 입력해 주세요.") String body) {
	}
}
