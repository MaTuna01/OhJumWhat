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
import com.ohjumwhat.place.PlaceLinkResolver;
import com.ohjumwhat.vote.VoteService;

@RestController
public class PollController {

	private final PollService pollService;

	private final MenuService menuService;

	private final VoteService voteService;

	private final PlaceLinkResolver placeLinkResolver;

	public PollController(PollService pollService, MenuService menuService, VoteService voteService,
			PlaceLinkResolver placeLinkResolver) {
		this.pollService = pollService;
		this.menuService = menuService;
		this.voteService = voteService;
		this.placeLinkResolver = placeLinkResolver;
	}

	@GetMapping("/api/orgs/{orgId}/polls/today")
	List<PollSummaryResponse> today(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId) {
		return pollService.today(orgId, loginUser.getUserId());
	}

	/** 지난 투표(오늘 이전, 최신순 10개씩). page는 0부터 */
	@GetMapping("/api/orgs/{orgId}/polls/history")
	PollHistoryResponse history(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long orgId,
			@RequestParam(defaultValue = "0") int page) {
		return pollService.history(orgId, loginUser.getUserId(), page);
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
		// 식당 링크 확인(naver.me 요청)은 DB 트랜잭션 밖에서 한다.
		return menuService.add(pollId, loginUser.getUserId(), request.name(),
				placeLinkResolver.place(request.link(), request.placeName(), request.placeAddress(),
						request.kakaoPlaceId(), request.placeQuery()));
	}

	/** link와 kakaoPlaceId가 모두 비어 있으면 식당을 뺀다. */
	@PutMapping("/api/polls/{pollId}/options/{optionId}/link")
	PollDetailResponse changeLink(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@PathVariable Long optionId, @Valid @RequestBody PlaceRequest request) {
		return menuService.changePlace(pollId, optionId, loginUser.getUserId(),
				placeLinkResolver.place(request.link(), request.placeName(), request.placeAddress(),
						request.kakaoPlaceId(), request.placeQuery()));
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

	/**
	 * @param link 식당 지도 링크(선택). 지도 앱의 공유 문구를 통째로 붙여도 된다.
	 * @param placeName 식당 이름(선택, 링크가 있을 때만 저장). 공백을 정리한 뒤 100자까지
	 * @param placeAddress 식당 주소(선택, 링크가 있을 때만 저장). 공유 글의 주소 줄. 공백을 정리한 뒤 200자까지
	 * @param kakaoPlaceId 근처 식당 찾기로 고른 카카오 장소 ID(선택). link와 함께 보내면 400
	 * @param placeQuery 그 식당을 찾을 때 친 검색어(kakaoPlaceId가 있을 때만 저장)
	 */
	record AddOptionRequest(
			@NotBlank(message = "메뉴 이름을 입력해 주세요.")
			@Size(max = 50, message = "메뉴 이름은 50자 이하로 입력해 주세요.")
			String name,
			@Size(max = 1000, message = "링크가 너무 길어요.")
			String link,
			@Size(max = 200, message = "식당 이름은 100자 이하로 입력해 주세요.")
			String placeName,
			@Size(max = 400, message = "주소는 200자 이하로 입력해 주세요.")
			String placeAddress,
			@Size(max = 40, message = "식당 정보가 올바르지 않아요. 다시 골라 주세요.")
			String kakaoPlaceId,
			@Size(max = 200, message = "검색어는 100자 이하로 입력해 주세요.")
			String placeQuery) {
	}

	/** 식당 달기·고치기. link와 kakaoPlaceId가 모두 비면 식당을 뺀다. */
	record PlaceRequest(
			@Size(max = 1000, message = "링크가 너무 길어요.")
			String link,
			@Size(max = 200, message = "식당 이름은 100자 이하로 입력해 주세요.")
			String placeName,
			@Size(max = 400, message = "주소는 200자 이하로 입력해 주세요.")
			String placeAddress,
			@Size(max = 40, message = "식당 정보가 올바르지 않아요. 다시 골라 주세요.")
			String kakaoPlaceId,
			@Size(max = 200, message = "검색어는 100자 이하로 입력해 주세요.")
			String placeQuery) {
	}

	record VoteRequest(Long optionId) {
	}
}
