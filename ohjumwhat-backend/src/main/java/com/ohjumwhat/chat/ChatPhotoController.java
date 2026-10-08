package com.ohjumwhat.chat;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

/**
 * 채팅 사진 보내기·보기. 보기는 프로필 사진과 달리 그 투표 조직의 멤버(또는 관리자)만, 지우지 않았고 보관 기간(30일) 안일 때만 된다.
 * 아니면 모두 같은 404다. 사진은 지워지거나 만료되므로 immutable·1년이 아니라 보관 기간만큼만 캐시한다.
 */
@RestController
class ChatPhotoController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(30)).cachePrivate();

	private final ChatPhotoService chatPhotoService;

	private final ChatService chatService;

	private final ChatPhotoStorage storage;

	ChatPhotoController(ChatPhotoService chatPhotoService, ChatService chatService, ChatPhotoStorage storage) {
		this.chatPhotoService = chatPhotoService;
		this.chatService = chatService;
		this.storage = storage;
	}

	/**
	 * 사진 1장을 메시지 하나로 보낸다. consumes를 두지 않아 multipart가 아닌 요청도 415 대신 400 「사진 파일을 보내 주세요.」를 받는다
	 * (GlobalExceptionHandler).
	 */
	@Restricted(Restriction.CHAT)
	@PostMapping("/api/polls/{pollId}/messages/photo")
	@ResponseStatus(HttpStatus.CREATED)
	ChatMessageResponse send(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long pollId,
			@RequestParam("photo") MultipartFile photo) throws IOException {
		if (photo.isEmpty()) {
			throw ApiException.badRequest("사진 파일을 보내 주세요.");
		}
		return chatPhotoService.send(pollId, loginUser.getUserId(), photo.getBytes());
	}

	/** 원본(긴 변 1600px, 뷰어) */
	@GetMapping(ChatMessageResponse.PHOTO_PATH + "{key:[0-9a-f]+}.jpg")
	ResponseEntity<Resource> photo(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String key) {
		return serve(loginUser, key, false);
	}

	/** 썸네일(긴 변 480px, 말풍선) */
	@GetMapping(ChatMessageResponse.PHOTO_PATH + "{key:[0-9a-f]+}_t.jpg")
	ResponseEntity<Resource> thumbnail(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String key) {
		return serve(loginUser, key, true);
	}

	private ResponseEntity<Resource> serve(LoginUser loginUser, String key, boolean thumbnail) {
		if (!chatService.canViewPhoto(key, loginUser.getUserId())) {
			return ResponseEntity.notFound().build();
		}
		return storage.find(key, thumbnail)
			.<ResponseEntity<Resource>>map(file -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CACHE)
				.body(new FileSystemResource(file)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
