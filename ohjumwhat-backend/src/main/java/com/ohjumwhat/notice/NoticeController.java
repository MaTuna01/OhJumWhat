package com.ohjumwhat.notice;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

@RestController
@RequestMapping("/api/notices")
class NoticeController {

	private final NoticeService noticeService;

	NoticeController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	/** 새 소식(최신순 10개씩). page는 0부터 */
	@GetMapping
	NoticePageResponse list(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "0") int page) {
		return noticeService.list(loginUser.getUserId(), page);
	}

	/** 안 읽은 공지 수와 가장 최근 것(상단 바 🔔의 점, 조직 홈 배너) */
	@GetMapping("/unread")
	UnreadNoticesResponse unread(@AuthenticationPrincipal LoginUser loginUser) {
		return noticeService.unread(loginUser.getUserId());
	}

	/** 「새 소식」을 봤다(화면을 열거나 배너를 닫음). */
	@PostMapping("/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markSeen(@AuthenticationPrincipal LoginUser loginUser) {
		noticeService.markSeen(loginUser.getUserId());
	}
}
