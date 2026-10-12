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
import com.ohjumwhat.common.ApiLimit;
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.menu.MenuSuggestion;
import com.ohjumwhat.place.PlaceLinkResolver;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;
import com.ohjumwhat.sanction.Unrestricted;
import com.ohjumwhat.vote.VoteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.POLL)
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

	@Operation(summary = "오늘 투표 목록", description = "조직 홈(D04)의 투표 카드. 오늘(한국 날짜) 열린 투표를 진행 중 → 마감 순으로 준다. "
			+ "진행 중이거나 채팅이 열린 투표가 있으면 화면이 15초마다 다시 받는다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}/polls/today")
	List<PollSummaryResponse> today(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId) {
		return pollService.today(orgId, loginUser.getUserId());
	}

	/** 지난 투표(오늘 이전, 최신순 10개씩). page는 0부터 */
	@Operation(summary = "지난 투표", description = "조직 홈(D04-H)의 「지난 투표」. 오늘(한국 날짜) 이전 투표를 최신순으로 10개씩 준다. "
			+ "오늘 투표는 「오늘 투표 목록」이 맡는다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "페이지 번호가 올바르지 않아요.", when = "page가 0보다 작을 때")
	@GetMapping("/api/orgs/{orgId}/polls/history")
	PollHistoryResponse history(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "페이지(0부터)", example = "0") @RequestParam(defaultValue = "0") int page) {
		return pollService.history(orgId, loginUser.getUserId(), page);
	}

	@Operation(summary = "투표 만들기", description = "오늘(한국 날짜) 투표를 연다(04-M). 지금 열리고 closesAt(오늘의 HH:mm)에 마감된다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@ErrorCase(status = 400, message = "마감 시간은 지금보다 뒤여야 해요.", when = "마감 시간이 지났을 때")
	@Restricted(Restriction.POLL)
	@PostMapping("/api/orgs/{orgId}/polls")
	@ResponseStatus(HttpStatus.CREATED)
	PollDetailResponse create(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Valid @RequestBody PollRequest request) {
		return pollService.create(orgId, loginUser.getUserId(), request);
	}

	@Operation(summary = "투표 상세", description = "투표 상세(D05) 전체를 한 번에 그리는 응답. 진행 중이면 화면이 3초마다 폴링하고, "
			+ "status가 CLOSED로 바뀌면 결과 모드로 바꾸고 멈춘다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}/polls/{pollId}")
	PollDetailResponse get(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId) {
		return pollService.get(orgId, pollId, loginUser.getUserId());
	}

	@Operation(summary = "투표 수정", description = "제목과 마감 시간(투표 날짜의 HH:mm)을 바꾼다(05-M1). 진행 중인 투표만, 조직 멤버 누구나. "
			+ "정기 투표로 열린 투표도 이 투표만 바뀐다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 400, message = "마감 시간은 지금보다 뒤여야 해요.", when = "마감 시간이 지났을 때")
	@Restricted(Restriction.POLL)
	@PutMapping("/api/polls/{pollId}")
	PollDetailResponse update(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Valid @RequestBody PollRequest request) {
		return pollService.update(pollId, loginUser.getUserId(), request);
	}

	@Operation(summary = "지금 마감", description = "closesAt을 지금으로 당겨 바로 마감한다(05-M2). 응답은 마감된 투표 상세(결과 모드)다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 409, message = "방금 열린 투표예요. 잠시 후 다시 시도해 주세요.", when = "열린 지 1초 안일 때")
	@Restricted(Restriction.POLL)
	@PostMapping("/api/polls/{pollId}/close")
	PollDetailResponse close(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId) {
		return pollService.close(pollId, loginUser.getUserId());
	}

	@Operation(summary = "투표 삭제", description = "진행 중인 수동 투표를 지운다(05-M3). 정기 투표로 열린 투표는 스케줄러가 1분 안에 다시 열기 때문에 "
			+ "지울 수 없고 「지금 마감」을 안내한다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 409, message = "정기 투표는 삭제할 수 없어요. 대신 지금 마감해 주세요.", when = "정기 투표로 열린 투표일 때")
	@Restricted(Restriction.POLL)
	@DeleteMapping("/api/polls/{pollId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId) {
		pollService.delete(pollId, loginUser.getUserId());
	}

	@Operation(summary = "메뉴 자동완성", description = "메뉴 입력창의 자동완성. 같은 조직 지난 투표의 메뉴 이름을 중복 없이 주고, "
			+ "최근 7일 안에 먹은 메뉴는 뒤로 보낸다. 같은 이름의 메뉴에 지난번 붙인 식당(lastPlace)도 준다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없거나 멤버가 아닐 때")
	@GetMapping("/api/orgs/{orgId}/menu-names")
	List<MenuSuggestion> menuNames(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "조직 ID", example = "1") @PathVariable Long orgId,
			@Parameter(description = "입력한 글자(비우면 전체)", example = "김치") @RequestParam(defaultValue = "") String q) {
		return menuService.suggestions(orgId, loginUser.getUserId(), q);
	}

	@Operation(summary = "메뉴 추가", description = "메뉴를 올린다. 추가한 사람이 자동으로 참여하지는 않는다. 식당은 선택이고, "
			+ "지도 링크(link·placeName·placeAddress) **또는** 근처 식당 찾기로 고른 카카오 식당(kakaoPlaceId·placeQuery) 중 하나만 보낸다. "
			+ "naver.me 단축 링크는 서버가 정식 링크로 바꾼다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 409, message = "이미 있는 메뉴예요.", when = "같은 이름(앞뒤 공백 제거)의 메뉴가 있을 때")
	@ErrorCase(status = 409, message = "다른 사람의 변경과 겹쳤어요. 다시 시도해 주세요.", when = "같은 메뉴를 동시에 추가했을 때")
	@ErrorCase(status = 400, message = "지도 링크와 근처 식당을 함께 붙일 수 없어요.", when = "link와 kakaoPlaceId를 함께 보냈을 때")
	@ErrorCase(status = 400, message = "링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요.", when = "http/https 주소가 아닐 때")
	@ErrorCase(status = 400, message = "링크는 500자 이하로 입력해 주세요.", when = "정리한 링크가 500자를 넘을 때")
	@ErrorCase(status = 400, message = "공유 링크를 찾을 수 없어요. 네이버 지도에서 링크를 다시 복사해 주세요.", when = "naver.me가 없는 코드라고 답할 때")
	@Restricted(Restriction.POLL)
	@PostMapping("/api/polls/{pollId}/options")
	@ResponseStatus(HttpStatus.CREATED)
	PollDetailResponse addOption(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Valid @RequestBody AddOptionRequest request) {
		// 식당 링크 확인(naver.me 요청)은 DB 트랜잭션 밖에서 한다.
		return menuService.add(pollId, loginUser.getUserId(), request.name(),
				placeLinkResolver.place(request.link(), request.placeName(), request.placeAddress(),
						request.kakaoPlaceId(), request.placeQuery()));
	}

	/** link와 kakaoPlaceId가 모두 비어 있으면 식당을 뺀다. */
	@Operation(summary = "메뉴의 식당 달기·고치기", description = "메뉴를 추가한 사람이 진행 중에 식당을 달거나 고친다(05-M4). "
			+ "link와 kakaoPlaceId가 모두 비면 식당을 뺀다. 링크 규칙은 「메뉴 추가」와 같다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "이 투표의 메뉴가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 403, message = "메뉴를 추가한 사람만 링크를 고칠 수 있어요.", when = "남이 추가한 메뉴일 때")
	@ErrorCase(status = 400, message = "지도 링크와 근처 식당을 함께 붙일 수 없어요.", when = "link와 kakaoPlaceId를 함께 보냈을 때")
	@ErrorCase(status = 400, message = "링크 주소가 올바르지 않아요. 지도 앱의 공유 링크를 붙여 주세요.", when = "http/https 주소가 아닐 때")
	@ErrorCase(status = 400, message = "링크는 500자 이하로 입력해 주세요.", when = "정리한 링크가 500자를 넘을 때")
	@ErrorCase(status = 400, message = "공유 링크를 찾을 수 없어요. 네이버 지도에서 링크를 다시 복사해 주세요.", when = "naver.me가 없는 코드라고 답할 때")
	@Restricted(Restriction.POLL)
	@PutMapping("/api/polls/{pollId}/options/{optionId}/link")
	PollDetailResponse changeLink(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId, @Valid @RequestBody PlaceRequest request) {
		return menuService.changePlace(pollId, optionId, loginUser.getUserId(),
				placeLinkResolver.place(request.link(), request.placeName(), request.placeAddress(),
						request.kakaoPlaceId(), request.placeQuery()));
	}

	@Operation(summary = "메뉴 삭제", description = "추가한 사람만, 참여자가 0명이고 투표가 진행 중일 때 지운다. 댓글도 함께 지워진다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "이 투표의 메뉴가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 403, message = "메뉴를 추가한 사람만 삭제할 수 있어요.", when = "남이 추가한 메뉴일 때")
	@ErrorCase(status = 409, message = "참여한 사람이 있는 메뉴는 삭제할 수 없어요.", when = "참여자가 있을 때")
	@Unrestricted
	@DeleteMapping("/api/polls/{pollId}/options/{optionId}")
	PollDetailResponse deleteOption(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId) {
		return menuService.delete(pollId, optionId, loginUser.getUserId());
	}

	/** optionId가 null이면 "오늘은 패스" */
	@Operation(summary = "참여·패스", description = "메뉴를 고르거나(optionId) 「오늘은 패스」(optionId = null)한다. 한 사람 한 응답이라 "
			+ "다시 보내면 바뀐다. 어떤 제재로도 막지 않는다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@ErrorCase(status = 400, message = "이 투표의 메뉴가 아니에요.", when = "다른 투표의 메뉴 ID일 때")
	@Unrestricted
	@PutMapping("/api/polls/{pollId}/vote")
	PollDetailResponse vote(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@RequestBody VoteRequest request) {
		return voteService.vote(pollId, loginUser.getUserId(), request.optionId());
	}

	/** 참여·패스 취소(미응답으로) */
	@Operation(summary = "참여·패스 취소", description = "응답을 지워 처음처럼 미응답으로 돌아간다. 응답이 없어도 같은 응답을 준다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "마감된 투표예요.", when = "투표가 마감됐을 때")
	@Unrestricted
	@DeleteMapping("/api/polls/{pollId}/vote")
	PollDetailResponse cancelVote(@AuthenticationPrincipal LoginUser loginUser, @Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId) {
		return voteService.cancel(pollId, loginUser.getUserId());
	}

	/**
	 * @param link 식당 지도 링크(선택). 지도 앱의 공유 문구를 통째로 붙여도 된다.
	 * @param placeName 식당 이름(선택, 링크가 있을 때만 저장). 공백을 정리한 뒤 100자까지
	 * @param placeAddress 식당 주소(선택, 링크가 있을 때만 저장). 공유 글의 주소 줄. 공백을 정리한 뒤 200자까지
	 * @param kakaoPlaceId 근처 식당 찾기로 고른 카카오 장소 ID(선택). link와 함께 보내면 400
	 * @param placeQuery 그 식당을 찾을 때 친 검색어(kakaoPlaceId가 있을 때만 저장)
	 */
	record AddOptionRequest(
			@Schema(description = "메뉴 이름(앞뒤 공백 제거, 50자)", example = "김치찌개", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotBlank(message = "메뉴 이름을 입력해 주세요.")
			@Size(max = 50, message = "메뉴 이름은 50자 이하로 입력해 주세요.")
			String name,
			@Schema(description = "식당 지도 링크(선택). 지도 앱의 공유 문구를 통째로 붙여도 된다", example = "https://naver.me/abcd1234")
			@Size(max = 1000, message = "링크가 너무 길어요.")
			String link,
			@Schema(description = "식당 이름(선택, link가 있을 때만 저장, 100자)", example = "할매집")
			@ApiLimit(100)
			@Size(max = 200, message = "식당 이름은 100자 이하로 입력해 주세요.")
			String placeName,
			@Schema(description = "식당 주소(선택, link가 있을 때만 저장, 200자)", example = "서울 강남구 테헤란로 123")
			@ApiLimit(200)
			@Size(max = 400, message = "주소는 200자 이하로 입력해 주세요.")
			String placeAddress,
			@Schema(description = "근처 식당 찾기로 고른 카카오 장소 ID(선택). link와 함께 보내면 400", example = "12345678")
			@Size(max = 40, message = "식당 정보가 올바르지 않아요. 다시 골라 주세요.")
			String kakaoPlaceId,
			@Schema(description = "그 카카오 식당을 찾을 때 친 검색어(kakaoPlaceId가 있을 때만 저장, 100자)", example = "김치찌개")
			@ApiLimit(100)
			@Size(max = 200, message = "검색어는 100자 이하로 입력해 주세요.")
			String placeQuery) {
	}

	/** 식당 달기·고치기. link와 kakaoPlaceId가 모두 비면 식당을 뺀다. */
	record PlaceRequest(
			@Schema(description = "식당 지도 링크", example = "https://map.naver.com/p/entry/place/1234567")
			@Size(max = 1000, message = "링크가 너무 길어요.")
			String link,
			@Schema(description = "식당 이름(link가 있을 때만 저장, 100자)", example = "할매집")
			@ApiLimit(100)
			@Size(max = 200, message = "식당 이름은 100자 이하로 입력해 주세요.")
			String placeName,
			@Schema(description = "식당 주소(link가 있을 때만 저장, 200자)", example = "서울 강남구 테헤란로 123")
			@ApiLimit(200)
			@Size(max = 400, message = "주소는 200자 이하로 입력해 주세요.")
			String placeAddress,
			@Schema(description = "카카오 장소 ID. link와 함께 보내면 400", example = "12345678")
			@Size(max = 40, message = "식당 정보가 올바르지 않아요. 다시 골라 주세요.")
			String kakaoPlaceId,
			@Schema(description = "카카오 식당을 찾을 때 친 검색어(100자)", example = "김치찌개")
			@ApiLimit(100)
			@Size(max = 200, message = "검색어는 100자 이하로 입력해 주세요.")
			String placeQuery) {
	}

	record VoteRequest(
			@Schema(description = "고른 메뉴 ID. null이면 「오늘은 패스」", example = "100", nullable = true)
			Long optionId) {
	}
}
