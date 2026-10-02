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

/**
 * 올린 프로필 사진 보내기. /api 아래라 로그인한 사람만 볼 수 있다(같은 출처라 img 요청에도 세션 쿠키가 붙는다).
 * 새로 올리면 키(주소)가 바뀌므로 브라우저가 1년 동안 다시 묻지 않고 캐시해도 된다.
 */
@RestController
public class ProfilePhotoController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

	private final ProfilePhotoStorage storage;

	public ProfilePhotoController(ProfilePhotoStorage storage) {
		this.storage = storage;
	}

	/** 키의 길이·형식은 저장소가 다시 확인한다. */
	@GetMapping(User.PHOTO_PATH + "{key:[0-9a-f]+}.jpg")
	ResponseEntity<Resource> photo(@PathVariable String key) {
		return storage.find(key)
			.<ResponseEntity<Resource>>map(file -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CACHE)
				.body(new FileSystemResource(file)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
