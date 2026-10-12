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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Unrestricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = ApiTags.NOTICE)
@RestController
@RequestMapping("/api/notices")
class NoticeController {

	private final NoticeService noticeService;

	NoticeController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	/** 새 소식(최신순 10개씩). page는 0부터 */
	@Operation(summary = "새 소식", description = "새 소식(D09) 한 쪽. 업데이트 글과 개발자 노트를 게시 시각 최신순으로 10개씩 준다. "
			+ "unread(NEW)는 이 요청 시점의 읽음 기준이라, 화면은 연 뒤 「새 소식 봤음」을 보내도 처음 받은 NEW를 그대로 보여준다.")
	@ErrorCase(status = 400, message = "페이지 번호가 올바르지 않아요.", when = "page가 0보다 작을 때")
	@GetMapping
	NoticePageResponse list(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "페이지(0부터)", example = "0") @RequestParam(defaultValue = "0") int page) {
		return noticeService.list(loginUser.getUserId(), page);
	}

	/** 안 읽은 공지 수와 가장 최근 것(상단 바 🔔의 점, 조직 홈 배너) */
	@Operation(summary = "안 읽은 새 소식", description = "상단 바 종 아이콘의 점과 조직 홈 맨 위 배너(04-B)에 쓴다. 안 읽음 = 마지막으로 본 뒤"
			+ "(한 번도 안 봤으면 가입한 뒤)에 게시된 공지다. 공지별 읽음은 없다.")
	@GetMapping("/unread")
	UnreadNoticesResponse unread(@AuthenticationPrincipal LoginUser loginUser) {
		return noticeService.unread(loginUser.getUserId());
	}

	/** 「새 소식」을 봤다(화면을 열거나 배너를 닫음). */
	@Operation(summary = "새 소식 봤음", description = "새 소식 화면(D09)을 열거나 배너(04-B)를 닫으면 보낸다. 지금까지 게시된 공지를 모두 읽은 것으로 한다. "
			+ "성공하면 204(본문 없음).")
	@Unrestricted
	@PostMapping("/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markSeen(@AuthenticationPrincipal LoginUser loginUser) {
		noticeService.markSeen(loginUser.getUserId());
	}
}
