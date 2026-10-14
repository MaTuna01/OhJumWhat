package com.ohjumwhat.user;

import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.common.ApiTags;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 올린 프로필 사진 보내기. /api 아래라 로그인한 사람만 볼 수 있다(같은 출처라 img 요청에도 세션 쿠키가 붙는다).
 * 새로 올리면 키(주소)가 바뀌므로 브라우저가 1년 동안 다시 묻지 않고 캐시해도 된다.
 */
@Tag(name = ApiTags.ME)
@RestController
public class ProfilePhotoController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

	private final ProfilePhotoStorage storage;

	public ProfilePhotoController(ProfilePhotoStorage storage) {
		this.storage = storage;
	}

	/** 키의 길이·형식은 저장소가 다시 확인한다. */
	@Operation(summary = "프로필 사진 보기", description = "올린 프로필 사진(256×256 JPEG). 응답의 profileImageUrl이 이 주소이면 "
			+ "`<img src>`에 그대로 넣는다(같은 출처라 세션 쿠키가 붙는다). 새로 올리면 주소가 바뀌므로 "
			+ "`Cache-Control: private, max-age=1년, immutable`로 캐시한다.")
	@ApiResponse(responseCode = "200", description = "사진(JPEG)",
			content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "404", description = "그 키의 사진이 없어요(지웠거나 바뀜). **본문 없음**", content = @Content)
	@GetMapping(User.PHOTO_PATH + "{key:[0-9a-f]+}.jpg")
	ResponseEntity<Resource> photo(
			@Parameter(description = "사진 키(소문자 16진수 32자). profileImageUrl /api/photos/{key}.jpg의 key",
					example = "3f2a9c1e5b7d4f60a1b2c3d4e5f60718")
			@PathVariable String key) {
		return storage.find(key)
			.<ResponseEntity<Resource>>map(file -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CACHE)
				.body(new FileSystemResource(file)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
