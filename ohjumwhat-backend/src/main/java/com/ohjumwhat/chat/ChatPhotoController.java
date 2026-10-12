package com.ohjumwhat.chat;

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
import com.ohjumwhat.common.ApiTags;
import com.ohjumwhat.common.ErrorCase;
import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 채팅 사진 보내기·보기. 보기는 프로필 사진과 달리 그 투표 조직의 멤버(또는 관리자)만, 지우지 않았고 보관 기간(30일) 안일 때만 된다.
 * 아니면 모두 같은 404다. 사진은 지워지거나(관리자 포함) 만료되므로 immutable·1년이 아니라 하루만 브라우저에 캐시한다.
 */
@Tag(name = ApiTags.CHAT)
@RestController
class ChatPhotoController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

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
	@Operation(summary = "채팅 사진 보내기", description = "사진 1장을 글 없는 메시지 하나로 보낸다(05-C7·05-C8, multipart 파트 `photo`). "
			+ "JPEG·PNG, 한 변 2048px 이하만 받고, 서버가 원본(긴 변 1600px)·썸네일(긴 변 480px) JPEG로 다시 그린다(EXIF 제거). "
			+ "화면은 보내기 전에 긴 변 1600px JPEG로 줄여 보내고, 여러 장이면 한 장씩 차례로 부른다. "
			+ "속도 제한은 메시지(10초에 10개)와 사진(10분에 20장)을 함께 건다. 사진은 30일 동안 보관한다.")
	@ErrorCase(status = 404, message = "투표를 찾을 수 없어요.", when = "투표가 없거나 그 조직 멤버가 아닐 때")
	@ErrorCase(status = 409, message = "채팅이 닫혔어요.", when = "마감 1시간 뒤가 지났을 때")
	@ErrorCase(status = 429, message = "메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요.", when = "10초에 메시지 10개를 넘을 때")
	@ErrorCase(status = 429, message = "사진을 너무 많이 보내고 있어요. 잠시 후 다시 보내 주세요.", when = "10분에 사진 20장을 넘을 때")
	@ErrorCase(status = 400, message = "사진 파일을 보내 주세요.", when = "multipart가 아니거나 photo 파트가 없거나 빈 파일일 때")
	@ErrorCase(status = 400, message = "이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요.", when = "JPEG·PNG가 아니거나 깨진 파일일 때")
	@ErrorCase(status = 400, message = "사진이 너무 커요. 다른 사진을 골라 주세요.", when = "한 변이 2048px보다 클 때")
	@ErrorCase(status = 503, message = "사진을 보내는 사람이 많아요. 잠시 뒤 다시 보내 주세요.", when = "사진 처리 자리(동시에 2장)가 2초 안에 나지 않을 때")
	// consumes가 없어 springdoc이 본문을 JSON으로 적으므로, multipart 본문은 여기서 적고 파라미터는 명세에서 숨긴다.
	@RequestBody(required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
			schema = @Schema(type = "object", requiredProperties = "photo"),
			schemaProperties = @SchemaProperty(name = "photo", schema = @Schema(type = "string", format = "binary",
					description = "사진 파일(JPEG·PNG, 5MB·한 변 2048px 이하)"))))
	@Restricted(Restriction.CHAT)
	@PostMapping("/api/polls/{pollId}/messages/photo")
	@ResponseStatus(HttpStatus.CREATED)
	ChatMessageResponse send(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "투표 ID", example = "10") @PathVariable Long pollId,
			@Parameter(hidden = true) @RequestParam("photo") MultipartFile photo) {
		if (photo.isEmpty()) {
			throw ApiException.badRequest("사진 파일을 보내 주세요.");
		}
		return chatPhotoService.send(pollId, loginUser.getUserId(), photo);
	}

	/** 원본(긴 변 1600px, 뷰어) */
	@Operation(summary = "채팅 사진 원본", description = "사진 뷰어(05-I)에서 크게 보는 원본(긴 변 1600px JPEG). 주소는 메시지의 photo.url을 그대로 쓴다. "
			+ "그 투표 조직의 멤버(또는 관리자)이고, 지우지 않았고, 보낸 지 30일 안일 때만 준다. 브라우저에 하루 캐시한다(private).")
	@ApiResponse(responseCode = "200", description = "JPEG 사진(Cache-Control: private, max-age=86400)",
			content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "404", description = "없는 사진, 멤버가 아님, 지운 사진, 보관 기간(30일)이 지난 사진. **본문 없음**",
			content = @Content)
	@GetMapping(ChatMessageResponse.PHOTO_PATH + "{key:[0-9a-f]+}.jpg")
	ResponseEntity<Resource> photo(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "사진 키(소문자 16진수 32자)", example = "3f2a9c1e5b7d4a608c1e2f3a4b5c6d7e") @PathVariable String key) {
		return serve(loginUser, key, false);
	}

	/** 썸네일(긴 변 480px, 말풍선) */
	@Operation(summary = "채팅 사진 썸네일", description = "채팅 말풍선의 썸네일(긴 변 480px JPEG). 주소는 메시지의 photo.thumbnailUrl을 그대로 쓴다. "
			+ "보는 조건과 캐시는 원본과 같다.")
	@ApiResponse(responseCode = "200", description = "JPEG 사진(Cache-Control: private, max-age=86400)",
			content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "404", description = "없는 사진, 멤버가 아님, 지운 사진, 보관 기간(30일)이 지난 사진. **본문 없음**",
			content = @Content)
	@GetMapping(ChatMessageResponse.PHOTO_PATH + "{key:[0-9a-f]+}_t.jpg")
	ResponseEntity<Resource> thumbnail(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "사진 키(소문자 16진수 32자)", example = "3f2a9c1e5b7d4a608c1e2f3a4b5c6d7e") @PathVariable String key) {
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
