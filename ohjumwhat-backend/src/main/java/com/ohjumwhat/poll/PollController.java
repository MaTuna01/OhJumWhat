package com.ohjumwhat.poll;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.vote.VoteService;

@RestController
public class PollController {

	private final PollService pollService;

	private final MenuService menuService;

	private final VoteService voteService;

	public PollController(PollService pollService, MenuService menuService, VoteService voteService) {
		this.pollService = pollService;
		this.menuService = menuService;
		this.voteService = voteService;
	}

	@GetMapping("/api/orgs/{orgId}/polls/today")
	List<PollSummaryResponse> today(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId) {
		return pollService.today(orgId, loginUser.getUserId());
	}

	@PostMapping("/api/orgs/{orgId}/polls")
	@ResponseStatus(HttpStatus.CREATED)
	PollDetailResponse create(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@Valid @RequestBody CreatePollRequest request) {
		return pollService.create(orgId, loginUser.getUserId(), request);
	}

	@GetMapping("/api/orgs/{orgId}/polls/{pollId}")
	PollDetailResponse get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@PathVariable Long pollId) {
		return pollService.get(orgId, pollId, loginUser.getUserId());
	}

	@GetMapping("/api/orgs/{orgId}/menu-names")
	List<String> menuNames(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@RequestParam(defaultValue = "") String q) {
		return menuService.suggestions(orgId, loginUser.getUserId(), q);
	}

	@PostMapping("/api/polls/{pollId}/options")
	@ResponseStatus(HttpStatus.CREATED)
	PollDetailResponse addOption(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@Valid @RequestBody AddOptionRequest request) {
		return menuService.add(pollId, loginUser.getUserId(), request.name());
	}

	@DeleteMapping("/api/polls/{pollId}/options/{optionId}")
	PollDetailResponse deleteOption(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId) {
		return menuService.delete(pollId, optionId, loginUser.getUserId());
	}

	/** optionId가 null이면 "오늘은 패스" */
	@PutMapping("/api/polls/{pollId}/vote")
	PollDetailResponse vote(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@RequestBody VoteRequest request) {
		return voteService.vote(pollId, loginUser.getUserId(), request.optionId());
	}

	record AddOptionRequest(
			@NotBlank(message = "메뉴 이름을 입력해 주세요.")
			@Size(max = 50, message = "메뉴 이름은 50자 이하로 입력해 주세요.")
			String name) {
	}

	record VoteRequest(Long optionId) {
	}
}
