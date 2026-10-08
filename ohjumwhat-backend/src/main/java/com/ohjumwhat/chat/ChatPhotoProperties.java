package com.ohjumwhat.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채팅 사진 파일을 두는 폴더(ohjumwhat.chat-photos.dir, 환경변수 CHAT_PHOTOS_DIR).
 * 비어 있으면 프로필 사진 폴더 아래 chat이다(운영 /data/photos/chat, 같은 Docker 볼륨, {@link ChatPhotoStorage}가 정한다).
 * 프로필 사진과 폴더를 나눠 프로필 사진 주소(키만 맞으면 보여준다)로 채팅 사진을 읽지 못하게 한다.
 */
@ConfigurationProperties("ohjumwhat.chat-photos")
public record ChatPhotoProperties(String dir) {

	public ChatPhotoProperties {
		dir = dir == null || dir.isBlank() ? null : dir.strip();
	}
}
