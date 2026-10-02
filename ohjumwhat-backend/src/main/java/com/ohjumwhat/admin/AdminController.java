package com.ohjumwhat.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.chat.ChatMessageResponse;
import com.ohjumwhat.chat.ChatMessagesResponse;
import com.ohjumwhat.chat.ChatService;
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

	AdminController(AdminService adminService, MenuCommentService menuCommentService, ChatService chatService) {
		this.adminService = adminService;
		this.menuCommentService = menuCommentService;
		this.chatService = chatService;
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
}
