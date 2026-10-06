package com.ohjumwhat.admin;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.chat.ChatMessageResponse;
import com.ohjumwhat.chat.ChatMessagesResponse;
import com.ohjumwhat.chat.ChatService;
import com.ohjumwhat.guestbook.GuestbookReportResponse;
import com.ohjumwhat.guestbook.GuestbookService;
import com.ohjumwhat.letter.LetterReportResponse;
import com.ohjumwhat.letter.LetterService;
import com.ohjumwhat.menu.MenuCommentResponse;
import com.ohjumwhat.menu.MenuCommentService;
import com.ohjumwhat.organization.LeaveResponse;
import com.ohjumwhat.poll.PollDetailResponse;

/** 관리자 콘솔 API. /api/admin/**는 관리자만 호출할 수 있다(SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final AdminService adminService;

	private final MenuCommentService menuCommentService;

	private final ChatService chatService;

	private final LetterService letterService;

	private final GuestbookService guestbookService;

	AdminController(AdminService adminService, MenuCommentService menuCommentService, ChatService chatService,
			LetterService letterService, GuestbookService guestbookService) {
		this.adminService = adminService;
		this.menuCommentService = menuCommentService;
		this.chatService = chatService;
		this.letterService = letterService;
		this.guestbookService = guestbookService;
	}

	@GetMapping("/stats")
	AdminResponses.Stats stats() {
		return adminService.stats();
	}

	@GetMapping("/users")
	List<AdminResponses.UserRow> users(@RequestParam(required = false) String q) {
		return adminService.users(q);
	}

	@GetMapping("/users/{userId}")
	AdminResponses.UserDetail user(@PathVariable Long userId) {
		return adminService.user(userId);
	}

	@DeleteMapping("/users/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId) {
		adminService.withdraw(admin.getUserId(), userId);
	}

	@DeleteMapping("/users/{userId}/photo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteUserPhoto(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId) {
		adminService.deleteUserPhoto(admin.getUserId(), userId);
	}

	/*
	 * 회원 프로필 수정(별명·소개·상세 프로필). 규칙과 오류 문구는 마이페이지(MeController)와 같고, 본인에게 따로 알리지 않는다.
	 * 모두 바뀐 회원 상세(GET /users/{userId}와 같은 응답)를 돌려준다.
	 */

	/** 별명 바꾸기. nickname이 비어 있으면 구글 이름으로 돌아간다. */
	@PutMapping("/users/{userId}/nickname")
	AdminResponses.UserDetail changeNickname(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId,
			@Valid @RequestBody NicknameRequest request) {
		return adminService.changeNickname(admin.getUserId(), userId, request.nickname());
	}

	/** 한줄 소개와 좋아하는 음식 바꾸기(통째로 바꾼다). bio가 비면 소개를, foodTags가 비면 음식을 지운다. */
	@PutMapping("/users/{userId}/profile")
	AdminResponses.UserDetail changeIntro(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId,
			@Valid @RequestBody IntroRequest request) {
		return adminService.changeIntro(admin.getUserId(), userId, request.bio(), request.foodTags());
	}

	/** 상세 프로필 바꾸기(다섯 항목 모두 필수, 통째로 바꾼다). */
	@PutMapping("/users/{userId}/profile/details")
	AdminResponses.UserDetail changeDetails(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId,
			@Valid @RequestBody DetailsRequest request) {
		return adminService.changeDetails(admin.getUserId(), userId, request.mbti(), request.personalColor(),
				request.hobbies(), request.age(), request.jobTitle());
	}

	/** 상세 프로필 지우기(다섯 항목을 한꺼번에) */
	@DeleteMapping("/users/{userId}/profile/details")
	AdminResponses.UserDetail clearDetails(@AuthenticationPrincipal LoginUser admin, @PathVariable Long userId) {
		return adminService.clearDetails(admin.getUserId(), userId);
	}

	/** 쪽지 신고(받은 사람이 신고한 쪽지만, 익명이어도 실제 보낸 사람까지). status: open(기본)·all */
	@GetMapping("/letter-reports")
	List<LetterReportResponse> letterReports(@RequestParam(defaultValue = "open") String status) {
		return letterService.reportsForAdmin(!"all".equalsIgnoreCase(status));
	}

	@PostMapping("/letter-reports/{reportId}/resolve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resolveLetterReport(@AuthenticationPrincipal LoginUser admin, @PathVariable Long reportId) {
		letterService.resolveReport(admin.getUserId(), reportId);
	}

	/** 방명록 신고(주인이 신고한 글, 제한·삭제돼도 원문). status: open(기본)·all */
	@GetMapping("/guestbook-reports")
	List<GuestbookReportResponse> guestbookReports(@RequestParam(defaultValue = "open") String status) {
		return guestbookService.reportsForAdmin(!"all".equalsIgnoreCase(status));
	}

	/** 글 제한: 본문을 누구에게도 내보내지 않고 쓴 사람에게 경고한다(지운 글도 제한한다). */
	@PostMapping("/guestbook-reports/{reportId}/restrict")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void restrictGuestbookReport(@AuthenticationPrincipal LoginUser admin, @PathVariable Long reportId) {
		guestbookService.restrictReport(admin.getUserId(), reportId);
	}

	/** 문제 없음: 신고만 처리하고 글은 그대로 둔다. */
	@PostMapping("/guestbook-reports/{reportId}/dismiss")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void dismissGuestbookReport(@AuthenticationPrincipal LoginUser admin, @PathVariable Long reportId) {
		guestbookService.dismissReport(admin.getUserId(), reportId);
	}

	@GetMapping("/blocks")
	List<AdminResponses.Block> blocks() {
		return adminService.blocks();
	}

	@DeleteMapping("/blocks/{blockId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unblock(@AuthenticationPrincipal LoginUser admin, @PathVariable Long blockId) {
		adminService.unblock(admin.getUserId(), blockId);
	}

	@GetMapping("/orgs")
	List<AdminResponses.OrganizationRow> organizations(@RequestParam(required = false) String q) {
		return adminService.organizations(q);
	}

	@GetMapping("/orgs/{organizationId}")
	AdminResponses.OrganizationDetail organization(@PathVariable Long organizationId) {
		return adminService.organization(organizationId);
	}

	@DeleteMapping("/orgs/{organizationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteOrganization(@AuthenticationPrincipal LoginUser admin, @PathVariable Long organizationId) {
		adminService.deleteOrganization(admin.getUserId(), organizationId);
	}

	@DeleteMapping("/orgs/{organizationId}/members/{userId}")
	LeaveResponse removeMember(@AuthenticationPrincipal LoginUser admin, @PathVariable Long organizationId,
			@PathVariable Long userId) {
		return adminService.removeMember(admin.getUserId(), organizationId, userId);
	}

	@GetMapping("/polls/{pollId}")
	PollDetailResponse poll(@AuthenticationPrincipal LoginUser admin, @PathVariable Long pollId) {
		return adminService.poll(admin.getUserId(), pollId);
	}

	/** withSchedule=true면 정기 투표 규칙도 함께 지운다(진행 중인 정기 투표가 다시 열리지 않게). */
	@DeleteMapping("/polls/{pollId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePoll(@AuthenticationPrincipal LoginUser admin, @PathVariable Long pollId,
			@RequestParam(defaultValue = "false") boolean withSchedule) {
		adminService.deletePoll(admin.getUserId(), pollId, withSchedule);
	}

	@DeleteMapping("/menu-options/{optionId}")
	PollDetailResponse deleteMenuOption(@AuthenticationPrincipal LoginUser admin, @PathVariable Long optionId) {
		return adminService.deleteMenuOption(admin.getUserId(), optionId);
	}

	@GetMapping("/menu-options/{optionId}/comments")
	List<MenuCommentResponse> menuComments(@AuthenticationPrincipal LoginUser admin, @PathVariable Long optionId) {
		return menuCommentService.listForAdmin(optionId, admin.getUserId());
	}

	/** 댓글 강제 삭제(마감과 무관). 그 메뉴의 남은 댓글을 돌려준다. */
	@DeleteMapping("/menu-comments/{commentId}")
	List<MenuCommentResponse> deleteMenuComment(@AuthenticationPrincipal LoginUser admin,
			@PathVariable Long commentId) {
		return menuCommentService.deleteByAdmin(commentId, admin.getUserId());
	}

	/** 투표의 채팅(before = 메시지 ID보다 오래된 50개, 없으면 최신 50개) */
	@GetMapping("/polls/{pollId}/messages")
	ChatMessagesResponse pollMessages(@PathVariable Long pollId, @RequestParam(required = false) Long before) {
		return chatService.listForAdmin(pollId, before);
	}

	/** 채팅 메시지 강제 삭제(채팅이 닫힌 뒤에도). 「삭제된 메시지예요」로 남고, 보고 있는 사람에게 바로 반영된다. */
	@DeleteMapping("/chat-messages/{messageId}")
	ChatMessageResponse deleteChatMessage(@AuthenticationPrincipal LoginUser admin, @PathVariable Long messageId) {
		return chatService.deleteByAdmin(messageId, admin.getUserId());
	}

	@DeleteMapping("/schedules/{scheduleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteSchedule(@AuthenticationPrincipal LoginUser admin, @PathVariable Long scheduleId) {
		adminService.deleteSchedule(admin.getUserId(), scheduleId);
	}

	// 프로필 수정 요청: MeController와 같은 방어선(큰 요청을 막는 크기 제한)이다. 실제 규칙은 UserService가 확인한다.

	record NicknameRequest(@Size(max = 100, message = "이름은 20자 이하로 입력해 주세요.") String nickname) {
	}

	record IntroRequest(@Size(max = 200, message = "한줄 소개는 50자 이하로 입력해 주세요.") String bio,
			@Size(max = 20, message = "좋아하는 음식은 3개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "음식 이름은 10자 이하로 입력해 주세요.") String> foodTags) {
	}

	record DetailsRequest(@Size(max = 20, message = "4가지 성향을 모두 선택해 주세요.") String mbti,
			@Size(max = 40, message = "퍼스널컬러를 선택해 주세요.") String personalColor,
			@Size(max = 20, message = "취미는 5개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "취미는 10자 이하로 입력해 주세요.") String> hobbies,
			Integer age,
			@Size(max = 100, message = "직급을 입력해 주세요. (최대 15자)") String jobTitle) {
	}
}
