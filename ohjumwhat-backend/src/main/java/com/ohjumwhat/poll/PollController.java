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
import com.ohjumwhat.menu.MenuSuggestion;
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
			@Valid @RequestBody PollRequest request) {
		return pollService.create(orgId, loginUser.getUserId(), request);
	}

	@GetMapping("/api/orgs/{orgId}/polls/{pollId}")
	PollDetailResponse get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@PathVariable Long pollId) {
		return pollService.get(orgId, pollId, loginUser.getUserId());
	}

	@PutMapping("/api/polls/{pollId}")
	PollDetailResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@Valid @RequestBody PollRequest request) {
		return pollService.update(pollId, loginUser.getUserId(), request);
	}

	@PostMapping("/api/polls/{pollId}/close")
	PollDetailResponse close(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId) {
		return pollService.close(pollId, loginUser.getUserId());
	}

	@DeleteMapping("/api/polls/{pollId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId) {
		pollService.delete(pollId, loginUser.getUserId());
	}

	@GetMapping("/api/orgs/{orgId}/menu-names")
	List<MenuSuggestion> menuNames(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@RequestParam(defaultValue = "") String q) {
		return menuService.suggestions(orgId, loginUser.getUserId(), q);
	}

	@PostMapping("/api/polls/{pollId}/options")
	@ResponseStatus(HttpStatus.CREATED)
	PollDetailResponse addOption(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@Valid @RequestBody AddOptionRequest request) {
		return menuService.add(pollId, loginUser.getUserId(), request.name(), request.link());
	}

	/** link가 비어 있으면 링크를 지운다. */
	@PutMapping("/api/polls/{pollId}/options/{optionId}/link")
	PollDetailResponse changeLink(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId, @Valid @RequestBody LinkRequest request) {
		return menuService.changeLink(pollId, optionId, loginUser.getUserId(), request.link());
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

	/** link: 식당 지도 링크(선택). 지도 앱의 공유 문구를 통째로 붙여도 된다. */
	record AddOptionRequest(
			@NotBlank(message = "메뉴 이름을 입력해 주세요.")
			@Size(max = 50, message = "메뉴 이름은 50자 이하로 입력해 주세요.")
			String name,
			@Size(max = 1000, message = "링크가 너무 길어요.")
			String link) {
	}

	record LinkRequest(@Size(max = 1000, message = "링크가 너무 길어요.") String link) {
	}

	record VoteRequest(Long optionId) {
	}
}
