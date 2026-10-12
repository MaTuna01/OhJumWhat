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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.guestbook.GuestbookReportResponse;
import com.ohjumwhat.guestbook.GuestbookService;
import com.ohjumwhat.letter.LetterReportResponse;
import com.ohjumwhat.letter.LetterService;
import com.ohjumwhat.menu.MenuCommentResponse;
import com.ohjumwhat.menu.MenuCommentService;
import com.ohjumwhat.organization.LeaveResponse;
import com.ohjumwhat.poll.PollDetailResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자 콘솔 API. /api/admin/**는 관리자만 호출할 수 있다(SecurityConfig).
 * 여러 묶음(개요·회원·신고·차단·조직)에 걸쳐 있어 명세의 @Tag는 클래스가 아니라 메서드마다 단다.
 */
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

	@Tag(name = ApiTags.ADMIN_OVERVIEW)
	@Operation(summary = "개요 숫자", description = "관리자 개요(DA01)의 카드: 회원·조직·오늘 투표·진행 중 투표·최근 7일 가입·차단·"
			+ "처리할 신고(쪽지·방명록·프로필 합과 종류별)·제한 중인 회원 수.")
	@GetMapping("/stats")
	AdminResponses.Stats stats() {
		return adminService.stats();
	}

	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "회원 목록", description = "회원 목록(DA02). 검색어 q가 화면 이름(별명)·구글 이름·이메일에 들어 있는 회원을 "
			+ "가입 최신순으로 **최대 100명** 준다(대소문자 무시, 비우면 전체). 그 이상은 검색으로 좁힌다.")
	@GetMapping("/users")
	List<AdminResponses.UserRow> users(
			@Parameter(description = "검색어(별명·구글 이름·이메일의 일부, 앞뒤 공백 무시). 비우면 전체", example = "kim")
			@RequestParam(required = false) String q) {
		return adminService.users(q);
	}

	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "회원 상세", description = "회원 상세(DA03): 회원 줄, 프로필(별명·소개·좋아하는 음식·상세 프로필), "
			+ "최근 활동, 소속 조직, 활동 수, 처리 전 사람 신고 수. 이용 제한 이력은 「관리자 · 제재」 API로 따로 받는다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때(강제 탈퇴 포함)")
	@GetMapping("/users/{userId}")
	AdminResponses.UserDetail user(@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId) {
		return adminService.user(userId);
	}

	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "강제 탈퇴", description = "회원 상세의 「강제 탈퇴」(A03-M). 모든 조직에서 탈퇴시키고(마지막 멤버였던 조직은 삭제), "
			+ "같은 구글 계정을 차단(「차단」 탭)하고, 회원을 지우고 로그인 세션을 끝낸다. 응답은 지워지고 올린 메뉴·글은 "
			+ "「탈퇴한 사용자」로 남는다. 그 사람에 대한 처리 전 신고도 함께 처리된다. 되돌릴 수 없다. 성공하면 204.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@ErrorCase(status = 409, message = "자기 자신은 탈퇴시킬 수 없어요.", when = "나 자신일 때")
	@ErrorCase(status = 409, message = "관리자는 탈퇴시킬 수 없어요. 먼저 서버 설정에서 관리자를 해제해 주세요.", when = "다른 관리자일 때")
	@DeleteMapping("/users/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId) {
		adminService.withdraw(admin.getUserId(), userId);
	}

	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "올린 사진 지우기", description = "회원 상세의 「올린 사진 지우기」(A03-M2, 부적절한 사진 대응). 구글 사진으로 돌아가고 "
			+ "본인에게 알리지 않는다. 올린 사진이 없으면 아무것도 하지 않는다. 성공하면 204.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@DeleteMapping("/users/{userId}/photo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteUserPhoto(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId) {
		adminService.deleteUserPhoto(admin.getUserId(), userId);
	}

	/*
	 * 회원 프로필 수정(별명·소개·상세 프로필). 규칙과 오류 문구는 마이페이지(MeController)와 같고, 본인에게 따로 알리지 않는다.
	 * 모두 바뀐 회원 상세(GET /users/{userId}와 같은 응답)를 돌려준다.
	 */

	/** 별명 바꾸기. nickname이 비어 있으면 구글 이름으로 돌아간다. */
	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "회원 별명 바꾸기", description = "「프로필 수정」(A03-M3)의 이름. 규칙과 문구는 마이페이지와 같다(앞뒤·연속 공백 정리, 20자). "
			+ "비우면 별명을 지우고 구글 이름으로 돌아간다(구글 이름은 고칠 수 없다). 다른 관리자·나 자신도 고칠 수 있고 본인에게 알리지 않는다. "
			+ "바뀐 회원 상세를 준다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@ErrorCase(status = 400, message = "이름에 쓸 수 없는 문자가 있어요.", when = "제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "이름은 20자 이하로 입력해 주세요.", when = "정리한 이름이 20자를 넘을 때")
	@PutMapping("/users/{userId}/nickname")
	AdminResponses.UserDetail changeNickname(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId,
			@Valid @RequestBody NicknameRequest request) {
		return adminService.changeNickname(admin.getUserId(), userId, request.nickname());
	}

	/** 한줄 소개와 좋아하는 음식 바꾸기(통째로 바꾼다). bio가 비면 소개를, foodTags가 비면 음식을 지운다. */
	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "회원 소개 바꾸기", description = "「프로필 수정」(A03-M3)의 한줄 소개와 좋아하는 음식을 통째로 바꾼다. "
			+ "bio가 비면 소개를, foodTags가 비면 음식을 지운다. 규칙과 문구는 마이페이지와 같다. 바뀐 회원 상세를 준다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@ErrorCase(status = 400, message = "한줄 소개에 쓸 수 없는 문자가 있어요.", when = "소개에 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "한줄 소개는 50자 이하로 입력해 주세요.", when = "정리한 소개가 50자를 넘을 때")
	@ErrorCase(status = 400, message = "음식 이름에 쓸 수 없는 문자가 있어요.", when = "음식 이름에 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "음식 이름은 10자 이하로 입력해 주세요.", when = "음식 하나가 10자를 넘을 때")
	@ErrorCase(status = 400, message = "좋아하는 음식은 3개까지 적을 수 있어요.", when = "중복을 뺀 음식이 3개를 넘을 때")
	@PutMapping("/users/{userId}/profile")
	AdminResponses.UserDetail changeIntro(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId,
			@Valid @RequestBody IntroRequest request) {
		return adminService.changeIntro(admin.getUserId(), userId, request.bio(), request.foodTags());
	}

	/** 상세 프로필 바꾸기(다섯 항목 모두 필수, 통째로 바꾼다). */
	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "회원 상세 프로필 바꾸기", description = "「프로필 수정」(A03-M3)의 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급)을 "
			+ "통째로 바꾼다. 마이페이지처럼 **다섯 항목 모두 필수**이고, 화면 입력 순서대로 확인해 처음 걸린 항목의 문구로 답한다. "
			+ "비우려면 「상세 프로필 지우기」를 쓴다. 바뀐 회원 상세를 준다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@ErrorCase(status = 400, message = "4가지 성향을 모두 선택해 주세요.", when = "MBTI가 E/I·S/N·T/F·J/P 4글자가 아닐 때")
	@ErrorCase(status = 400, message = "퍼스널컬러를 선택해 주세요.", when = "퍼스널컬러가 없거나 모르는 값일 때")
	@ErrorCase(status = 400, message = "취미에 쓸 수 없는 문자가 있어요.", when = "취미에 제어 문자가 있을 때")
	@ErrorCase(status = 400, message = "취미는 10자 이하로 입력해 주세요.", when = "취미 하나가 10자를 넘을 때")
	@ErrorCase(status = 400, message = "취미는 5개까지 적을 수 있어요.", when = "중복을 뺀 취미가 5개를 넘을 때")
	@ErrorCase(status = 400, message = "최소 1개 이상의 취미를 등록해 주세요.", when = "취미가 없을 때")
	@ErrorCase(status = 400, message = "올바른 나이를 입력해 주세요. (1~120세)", when = "나이가 없거나 1~120이 아닐 때")
	@ErrorCase(status = 400, message = "직급을 입력해 주세요. (최대 15자)", when = "직급이 비었거나 15자를 넘을 때")
	@ErrorCase(status = 400, message = "직급에 쓸 수 없는 문자가 있어요.", when = "직급에 제어 문자가 있을 때")
	@PutMapping("/users/{userId}/profile/details")
	AdminResponses.UserDetail changeDetails(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId,
			@Valid @RequestBody DetailsRequest request) {
		return adminService.changeDetails(admin.getUserId(), userId, request.mbti(), request.personalColor(),
				request.hobbies(), request.age(), request.jobTitle());
	}

	/** 상세 프로필 지우기(다섯 항목을 한꺼번에) */
	@Tag(name = ApiTags.ADMIN_USER)
	@Operation(summary = "회원 상세 프로필 지우기", description = "「상세 프로필 지우기」(A03-M4 확인 창). 다섯 항목을 한꺼번에 비운다"
			+ "(「저장」 없이 바로). 본인은 다음에 들어오면 다시 채우기 안내를 본다. 이미 비어 있으면 그대로다. 바뀐 회원 상세를 준다.")
	@ErrorCase(status = 404, message = "회원을 찾을 수 없어요.", when = "회원이 없을 때")
	@DeleteMapping("/users/{userId}/profile/details")
	AdminResponses.UserDetail clearDetails(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "회원 ID", example = "7") @PathVariable Long userId) {
		return adminService.clearDetails(admin.getUserId(), userId);
	}

	/** 쪽지 신고(받은 사람이 신고한 쪽지만, 익명이어도 실제 보낸 사람까지). status: open(기본)·all */
	@Tag(name = ApiTags.ADMIN_REPORT)
	@Operation(summary = "쪽지 신고 목록", description = "「신고」 탭의 쪽지(DA09). 받은 사람이 신고한 쪽지만 보이고(신고되지 않은 쪽지는 "
			+ "관리자도 볼 수 없다), 익명 쪽지도 실제 보낸 사람을 준다. 최신 신고순으로 최대 100건.")
	@GetMapping("/letter-reports")
	List<LetterReportResponse> letterReports(
			@Parameter(description = "open = 처리 전만(기본), all = 처리한 것까지. 그 밖의 값은 open으로 본다", example = "open")
			@RequestParam(defaultValue = "open") String status) {
		return letterService.reportsForAdmin(!"all".equalsIgnoreCase(status));
	}

	@Tag(name = ApiTags.ADMIN_REPORT)
	@Operation(summary = "쪽지 신고 처리 완료", description = "쪽지 신고를 처리 완료로 바꾼다. 이미 처리했으면 그대로다. "
			+ "보낸 사람을 제재하려면 「관리자 · 제재」의 제재하기를 따로 부른다. 성공하면 204.")
	@ErrorCase(status = 404, message = "신고를 찾을 수 없어요.", when = "신고가 없을 때")
	@PostMapping("/letter-reports/{reportId}/resolve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resolveLetterReport(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "쪽지 신고 ID", example = "5") @PathVariable Long reportId) {
		letterService.resolveReport(admin.getUserId(), reportId);
	}

	/** 방명록 신고(주인이 신고한 글, 제한·삭제돼도 원문). status: open(기본)·all */
	@Tag(name = ApiTags.ADMIN_REPORT)
	@Operation(summary = "방명록 신고 목록", description = "「신고」 탭의 방명록(DA09b). 방명록 주인이 신고한 글을 원문(제한·삭제돼도)과 "
			+ "함께 최신 신고순으로 최대 100건 준다.")
	@GetMapping("/guestbook-reports")
	List<GuestbookReportResponse> guestbookReports(
			@Parameter(description = "open = 처리 전만(기본), all = 처리한 것까지. 그 밖의 값은 open으로 본다", example = "open")
			@RequestParam(defaultValue = "open") String status) {
		return guestbookService.reportsForAdmin(!"all".equalsIgnoreCase(status));
	}

	/** 글 제한: 본문을 누구에게도 내보내지 않고 쓴 사람에게 경고한다(지운 글도 제한한다). */
	@Tag(name = ApiTags.ADMIN_REPORT)
	@Operation(summary = "방명록 글 제한", description = "방명록 신고의 「글 제한」(A09b-M 확인 창). 그 글이 모두에게 "
			+ "「관리자에 의해 제한된 게시글입니다」로 보이고(지운 글도 제한한다), 쓴 사람에게 경고 창과 웹 푸시가 간다. "
			+ "이미 「글 제한」으로 처리했으면 그대로다. 성공하면 204.")
	@ErrorCase(status = 404, message = "신고를 찾을 수 없어요.", when = "신고가 없을 때")
	@ErrorCase(status = 409, message = "이미 처리한 신고예요.", when = "이미 「문제 없음」으로 처리했을 때")
	@PostMapping("/guestbook-reports/{reportId}/restrict")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void restrictGuestbookReport(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "방명록 신고 ID", example = "5") @PathVariable Long reportId) {
		guestbookService.restrictReport(admin.getUserId(), reportId);
	}

	/** 문제 없음: 신고만 처리하고 글은 그대로 둔다. */
	@Tag(name = ApiTags.ADMIN_REPORT)
	@Operation(summary = "방명록 신고 문제 없음", description = "방명록 신고의 「문제 없음」. 신고만 처리하고 글은 그대로 둔다. "
			+ "이미 「문제 없음」으로 처리했으면 그대로다. 성공하면 204.")
	@ErrorCase(status = 404, message = "신고를 찾을 수 없어요.", when = "신고가 없을 때")
	@ErrorCase(status = 409, message = "이미 처리한 신고예요.", when = "이미 「글 제한」으로 처리했을 때")
	@PostMapping("/guestbook-reports/{reportId}/dismiss")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void dismissGuestbookReport(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "방명록 신고 ID", example = "5") @PathVariable Long reportId) {
		guestbookService.dismissReport(admin.getUserId(), reportId);
	}

	@Tag(name = ApiTags.ADMIN_BLOCK)
	@Operation(summary = "차단 목록", description = "「차단」 탭(DA07). 강제 탈퇴하면서 차단한 구글 계정을 최근 차단순으로 모두 준다. "
			+ "차단된 계정으로 로그인하면 `/login?error=blocked`로 돌아간다.")
	@GetMapping("/blocks")
	List<AdminResponses.Block> blocks() {
		return adminService.blocks();
	}

	@Tag(name = ApiTags.ADMIN_BLOCK)
	@Operation(summary = "차단 해제", description = "구글 계정 차단을 푼다. 같은 계정으로 다시 로그인하면 **새 회원**으로 가입된다"
			+ "(지난 회원 정보는 돌아오지 않는다). 성공하면 204.")
	@ErrorCase(status = 404, message = "차단 기록을 찾을 수 없어요.", when = "차단 기록이 없을 때")
	@DeleteMapping("/blocks/{blockId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unblock(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "차단 기록 ID", example = "3") @PathVariable Long blockId) {
		adminService.unblock(admin.getUserId(), blockId);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "조직 목록", description = "조직 목록(DA04). 검색어 q가 조직 이름에 들어 있는 조직을 만든 최신순으로 "
			+ "**최대 100개** 준다(대소문자 무시, 비우면 전체).")
	@GetMapping("/orgs")
	List<AdminResponses.OrganizationRow> organizations(
			@Parameter(description = "검색어(조직 이름의 일부, 앞뒤 공백 무시). 비우면 전체", example = "개발")
			@RequestParam(required = false) String q) {
		return adminService.organizations(q);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "조직 상세", description = "조직 상세(DA05): 조직 줄, 멤버(들어온 순), 최근 투표 30개, 정기 투표 규칙. "
			+ "관리자는 조직 멤버가 아니어도 본다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없을 때")
	@GetMapping("/orgs/{organizationId}")
	AdminResponses.OrganizationDetail organization(
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long organizationId) {
		return adminService.organization(organizationId);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "조직 삭제", description = "조직을 지운다. 멤버·투표·메뉴·응답·댓글·채팅·정기 투표가 함께 지워지고, "
			+ "보고 있던 채팅 연결도 끊긴다. 되돌릴 수 없다. 성공하면 204.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없을 때")
	@DeleteMapping("/orgs/{organizationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteOrganization(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long organizationId) {
		adminService.deleteOrganization(admin.getUserId(), organizationId);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "멤버 내보내기", description = "조직 상세에서 멤버를 내보낸다. 본인 탈퇴와 같은 규칙이라 진행 중인 투표의 "
			+ "그 사람 응답이 지워지고, 마지막 멤버였다면 조직이 삭제된다(organizationDeleted). 초대 링크로는 다시 들어올 수 있다.")
	@ErrorCase(status = 404, message = "조직을 찾을 수 없어요.", when = "조직이 없을 때")
	@ErrorCase(status = 404, message = "이 조직의 멤버가 아니에요.", when = "그 회원이 이 조직 멤버가 아닐 때")
	@DeleteMapping("/orgs/{organizationId}/members/{userId}")
	LeaveResponse removeMember(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "조직 ID", example = "1") @PathVariable Long organizationId,
			@Parameter(description = "내보낼 회원 ID", example = "7") @PathVariable Long userId) {
		return adminService.removeMember(admin.getUserId(), organizationId, userId);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "투표 상세", description = "투표 상세(DA06). 사용자 화면의 투표 상세와 같은 모양이고, 관리자는 멤버가 아니어도 본다. "
			+ "myResponse·myOptionId·mine·deletable은 관리자 기준이라 화면에서 쓰지 않는다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없을 때")
	@GetMapping("/polls/{pollId}")
	PollDetailResponse poll(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId) {
		return adminService.poll(admin.getUserId(), pollId);
	}

	/** withSchedule=true면 정기 투표 규칙도 함께 지운다(진행 중인 정기 투표가 다시 열리지 않게). */
	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "투표 삭제", description = "투표를 지운다(진행 중·마감 상관없이). 메뉴·응답·댓글·채팅도 함께 지워진다. "
			+ "진행 중인 정기 투표는 지워도 스케줄러가 1분 안에 다시 열기 때문에 withSchedule=true로 규칙도 함께 지울 수 있다. 성공하면 204.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없을 때")
	@DeleteMapping("/polls/{pollId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePoll(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "true면 이 투표를 연 정기 투표 규칙도 함께 지운다(수동 투표면 무시)", example = "false")
			@RequestParam(defaultValue = "false") boolean withSchedule) {
		adminService.deletePoll(admin.getUserId(), pollId, withSchedule);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "메뉴 강제 삭제", description = "메뉴를 지운다(참여자가 있어도, 마감과 상관없이). 그 메뉴를 고른 사람은 미응답이 되고 "
			+ "댓글도 함께 지워진다. 최신 투표 상세를 준다.")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "메뉴가 없을 때")
	@DeleteMapping("/menu-options/{optionId}")
	PollDetailResponse deleteMenuOption(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId) {
		return adminService.deleteMenuOption(admin.getUserId(), optionId);
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "메뉴 댓글 목록", description = "투표 상세(DA06)에서 펼친 메뉴의 댓글(A06-K). 오래된 순으로 모두 준다. "
			+ "mine은 관리자 기준이다.")
	@ErrorCase(status = 404, message = "메뉴를 찾을 수 없어요.", when = "메뉴가 없을 때")
	@GetMapping("/menu-options/{optionId}/comments")
	List<MenuCommentResponse> menuComments(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "메뉴 ID", example = "100") @PathVariable Long optionId) {
		return menuCommentService.listForAdmin(optionId, admin.getUserId());
	}

	/** 댓글 강제 삭제(마감과 무관). 그 메뉴의 남은 댓글을 돌려준다. */
	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "메뉴 댓글 강제 삭제", description = "부적절한 댓글을 지운다(A06-K, 마감과 상관없이). 그 메뉴의 남은 댓글 목록을 준다.")
	@ErrorCase(status = 404, message = "댓글을 찾을 수 없어요.", when = "댓글이 없을 때")
	@DeleteMapping("/menu-comments/{commentId}")
	List<MenuCommentResponse> deleteMenuComment(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "댓글 ID", example = "200") @PathVariable Long commentId) {
		return menuCommentService.deleteByAdmin(commentId, admin.getUserId());
	}

	/** 투표의 채팅(before = 메시지 ID보다 오래된 50개, 없으면 최신 50개) */
	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "투표 채팅 보기", description = "투표 상세(DA06)의 채팅(A06-C). 채팅 기간과 상관없이 50개씩 오래된 → 최신 순으로 준다. "
			+ "before를 비우면 최신 50개, 주면 그 메시지보다 오래된 50개다(hasMore로 더 있는지). lastReadId는 늘 0이다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없을 때")
	@GetMapping("/polls/{pollId}/messages")
	ChatMessagesResponse pollMessages(@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(description = "이 메시지 ID보다 오래된 것을 받는다(비우면 최신 50개)", example = "500")
			@RequestParam(required = false) Long before) {
		return chatService.listForAdmin(pollId, before);
	}

	/** 채팅 메시지 강제 삭제(채팅이 닫힌 뒤에도). 「삭제된 메시지예요」로 남고, 보고 있는 사람에게 바로 반영된다. */
	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "채팅 메시지 강제 삭제", description = "부적절한 채팅 메시지를 지운다(A06-C, 채팅이 닫힌 뒤에도). "
			+ "「삭제된 메시지예요」로 남고 사진이면 사진도 지워지며, 보고 있는 사람에게 바로 반영된다. 지운 메시지를 준다.")
	@ErrorCase(status = 404, message = "메시지를 찾을 수 없어요.", when = "메시지가 없거나 이미 지웠을 때")
	@DeleteMapping("/chat-messages/{messageId}")
	ChatMessageResponse deleteChatMessage(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "채팅 메시지 ID", example = "500") @PathVariable Long messageId) {
		return chatService.deleteByAdmin(messageId, admin.getUserId());
	}

	@Tag(name = ApiTags.ADMIN_ORG)
	@Operation(summary = "정기 투표 규칙 삭제", description = "조직 상세(DA05)의 정기 투표 규칙을 지운다. 이미 열린 투표는 남는다"
			+ "(정기 투표 표시만 빠진다). 성공하면 204.")
	@ErrorCase(status = 404, message = "정기 투표 규칙을 찾을 수 없어요.", when = "규칙이 없을 때")
	@DeleteMapping("/schedules/{scheduleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteSchedule(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "정기 투표 규칙 ID", example = "4") @PathVariable Long scheduleId) {
		adminService.deleteSchedule(admin.getUserId(), scheduleId);
	}

	// 프로필 수정 요청: MeController와 같은 방어선(큰 요청을 막는 크기 제한)이다. 실제 규칙은 UserService가 확인한다.

	@Schema(name = "AdminNicknameRequest", description = "회원 별명 바꾸기")
	record NicknameRequest(
			@Schema(description = "별명(앞뒤·연속 공백 정리 뒤 20자). 비우거나 null이면 별명을 지우고 구글 이름으로 돌아간다",
					example = "점심요정", nullable = true)
			@Size(max = 100, message = "이름은 20자 이하로 입력해 주세요.") String nickname) {
	}

	@Schema(name = "AdminIntroRequest", description = "회원 한줄 소개·좋아하는 음식 바꾸기(통째로)")
	record IntroRequest(
			@Schema(description = "한줄 소개(한 줄, 공백 정리 뒤 50자). 비우면 지운다", example = "국물 요리면 다 좋아요", nullable = true)
			@Size(max = 200, message = "한줄 소개는 50자 이하로 입력해 주세요.") String bio,
			@Schema(description = "좋아하는 음식 태그(하나 10자, 앞의 # 제거, 띄어쓰기·대소문자만 다른 것은 먼저 적은 것만 남겨 3개까지). "
					+ "비우면 지운다", example = "[\"김치찌개\", \"떡볶이\"]", nullable = true)
			@Size(max = 20, message = "좋아하는 음식은 3개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "음식 이름은 10자 이하로 입력해 주세요.") String> foodTags) {
	}

	@Schema(name = "AdminDetailsRequest", description = "회원 상세 프로필 바꾸기(다섯 항목 모두 필수, 통째로)")
	record DetailsRequest(
			@Schema(description = "MBTI: E/I·S/N·T/F·J/P를 하나씩 고른 4글자(대소문자 상관없이, 대문자로 저장)", example = "ENFP",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 20, message = "4가지 성향을 모두 선택해 주세요.") String mbti,
			@Schema(description = "퍼스널컬러: SPRING_WARM 봄 웜, SUMMER_COOL 여름 쿨, AUTUMN_WARM 가을 웜, WINTER_COOL 겨울 쿨",
					example = "SPRING_WARM", requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 40, message = "퍼스널컬러를 선택해 주세요.") String personalColor,
			@Schema(description = "취미 태그 1~5개(하나 10자, 좋아하는 음식과 같은 정리 규칙)", example = "[\"등산\", \"보드게임\"]",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 20, message = "취미는 5개까지 적을 수 있어요.")
			List<@Size(max = 100, message = "취미는 10자 이하로 입력해 주세요.") String> hobbies,
			@Schema(description = "나이(1~120 정수)", example = "29", requiredMode = Schema.RequiredMode.REQUIRED)
			Integer age,
			@Schema(description = "직급(공백 정리 뒤 1~15자)", example = "사원", requiredMode = Schema.RequiredMode.REQUIRED)
			@Size(max = 100, message = "직급을 입력해 주세요. (최대 15자)") String jobTitle) {
	}
}
