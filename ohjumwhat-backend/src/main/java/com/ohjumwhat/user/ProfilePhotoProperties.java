package com.ohjumwhat.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프로필 사진 파일을 두는 폴더(ohjumwhat.photos.dir, 환경변수 PHOTOS_DIR).
 * 운영은 Docker 볼륨(/data/photos), 로컬은 비어 있으면 ./data/photos(백엔드 폴더 기준, gitignore 대상)다.
 */
@ConfigurationProperties("ohjumwhat.photos")
public record ProfilePhotoProperties(String dir) {

	static final String DEFAULT_DIR = "./data/photos";

	public ProfilePhotoProperties {
		dir = dir == null || dir.isBlank() ? DEFAULT_DIR : dir.strip();
	}
}
