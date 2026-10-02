package com.ohjumwhat.user;

import org.springframework.stereotype.Service;

/**
 * 프로필 사진 올리기·되돌리기. 이미지 처리와 파일 쓰기는 트랜잭션 밖에서 하고, DB의 키만 {@link UserService#changePhoto}가 바꾼다.
 * 키를 바꾸지 못하면(회원이 없어짐 등) 방금 쓴 파일을 지운다.
 */
@Service
public class ProfilePhotoService {

	private final UserService userService;

	private final ProfilePhotoStorage storage;

	public ProfilePhotoService(UserService userService, ProfilePhotoStorage storage) {
		this.userService = userService;
		this.storage = storage;
	}

	public MeResponse upload(Long userId, byte[] bytes) {
		String key = storage.write(ProfilePhotoImages.toAvatarJpeg(bytes));
		try {
			return userService.changePhoto(userId, key);
		}
		catch (RuntimeException e) {
			storage.delete(key);
			throw e;
		}
	}

	/** 올린 사진을 지우고 구글 사진으로 돌아간다. 올린 사진이 없어도 그대로 성공한다. */
	public MeResponse resetToGoogle(Long userId) {
		return userService.changePhoto(userId, null);
	}
}
