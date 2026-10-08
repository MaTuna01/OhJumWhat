package com.ohjumwhat.user;

import java.awt.image.BufferedImage;

import com.ohjumwhat.common.ImageFiles;

/**
 * 올린 사진을 프로필 사진(256×256 JPEG)으로 바꾼다. 브라우저가 이미 정사각형으로 잘라 보내지만, 서버에서 다시 그려
 * 저장하므로 EXIF(위치 정보 등) 같은 메타데이터와 이미지가 아닌 내용은 남지 않는다({@link ImageFiles}).
 */
final class ProfilePhotoImages {

	/** 저장하는 사진의 한 변(px). 화면의 가장 큰 아바타(80px)를 고해상도 화면에서도 선명하게 보여줄 수 있는 크기다. */
	static final int SIZE = 256;

	private ProfilePhotoImages() {
	}

	static byte[] toAvatarJpeg(byte[] bytes) {
		BufferedImage source = ImageFiles.read(bytes);
		int side = Math.min(source.getWidth(), source.getHeight());
		BufferedImage square = ImageFiles.flatten(source.getSubimage((source.getWidth() - side) / 2,
				(source.getHeight() - side) / 2, side, side));
		return ImageFiles.encode(ImageFiles.scale(square, SIZE, SIZE));
	}
}
