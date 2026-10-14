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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자 콘솔 「공지」: 개발자 노트 쓰기·고치기·지우기. 목록은 GET /api/notices를 같이 쓴다.
 * 업데이트 글(릴리스 노트)은 저장소 파일이 원본이라 여기서 고칠 수 없다.
 */
@Tag(name = ApiTags.ADMIN_NOTICE)
@RestController
@RequestMapping("/api/admin/notices")
class NoticeAdminController {

	private final NoticeService noticeService;

	NoticeAdminController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	@Operation(summary = "개발자 노트 쓰기", description = "관리자 콘솔 「공지」의 글쓰기 모달(A08-M). 지금 게시되어 모두에게 새 소식으로 알린다. "
			+ "목록은 사용자 API의 GET /api/notices를 같이 쓴다. 응답은 201과 게시한 공지다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	NoticePageResponse.Item create(@AuthenticationPrincipal LoginUser admin,
			@Valid @RequestBody NoticeRequest request) {
		return noticeService.createNote(admin.getUserId(), request);
	}

	/** 고쳐도 게시 시각은 그대로라 다시 알리지 않는다. */
	@Operation(summary = "개발자 노트 고치기", description = "제목·본문을 고친다(A08-M). 게시 시각은 그대로라 다시 알리지 않는다. "
			+ "업데이트 글(릴리스 노트)은 저장소 파일이 원본이라 고칠 수 없다.")
	@ErrorCase(status = 404, message = "공지를 찾을 수 없어요.", when = "공지가 없을 때")
	@ErrorCase(status = 409, message = "업데이트 글은 저장소 파일에서 고쳐요.", when = "업데이트 글(RELEASE)일 때")
	@PutMapping("/{noticeId}")
	NoticePageResponse.Item update(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "공지 ID", example = "12") @PathVariable Long noticeId,
			@Valid @RequestBody NoticeRequest request) {
		return noticeService.updateNote(admin.getUserId(), noticeId, request);
	}

	@Operation(summary = "개발자 노트 지우기", description = "개발자 노트를 지운다. 업데이트 글은 지울 수 없다. 성공하면 204(본문 없음).")
	@ErrorCase(status = 404, message = "공지를 찾을 수 없어요.", when = "공지가 없을 때")
	@ErrorCase(status = 409, message = "업데이트 글은 저장소 파일에서 고쳐요.", when = "업데이트 글(RELEASE)일 때")
	@DeleteMapping("/{noticeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser admin,
			@Parameter(description = "공지 ID", example = "12") @PathVariable Long noticeId) {
		noticeService.deleteNote(admin.getUserId(), noticeId);
	}
}
