package com.ohjumwhat.notice;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.auth.LoginUser;

/**
 * 관리자 콘솔 「공지」: 개발자 노트 쓰기·고치기·지우기. 목록은 GET /api/notices를 같이 쓴다.
 * 업데이트 글(릴리스 노트)은 저장소 파일이 원본이라 여기서 고칠 수 없다.
 */
@RestController
@RequestMapping("/api/admin/notices")
class NoticeAdminController {

	private final NoticeService noticeService;

	NoticeAdminController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	NoticePageResponse.Item create(@AuthenticationPrincipal LoginUser admin,
			@Valid @RequestBody NoticeRequest request) {
		return noticeService.createNote(admin.getUserId(), request);
	}

	/** 고쳐도 게시 시각은 그대로라 다시 알리지 않는다. */
	@PutMapping("/{noticeId}")
	NoticePageResponse.Item update(@AuthenticationPrincipal LoginUser admin, @PathVariable Long noticeId,
			@Valid @RequestBody NoticeRequest request) {
		return noticeService.updateNote(admin.getUserId(), noticeId, request);
	}

	@DeleteMapping("/{noticeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser admin, @PathVariable Long noticeId) {
		noticeService.deleteNote(admin.getUserId(), noticeId);
	}
}
