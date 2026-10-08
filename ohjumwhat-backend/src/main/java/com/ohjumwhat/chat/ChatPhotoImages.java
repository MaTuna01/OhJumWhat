package com.ohjumwhat.chat;

import java.awt.image.BufferedImage;

import com.ohjumwhat.common.ImageFiles;

/**
 * 채팅 사진을 원본(긴 변 1600px)과 썸네일(긴 변 480px) JPEG로 다시 그린다. 비율은 그대로 두고 키우지 않는다.
 * 브라우저가 긴 변 1600px로 줄여 보내지만 서버에서 다시 그려 메타데이터(EXIF 위치 정보 등)를 남기지 않는다.
 */
final class ChatPhotoImages {

	/** 원본의 긴 변(px). 휴대폰·데스크톱 뷰어에서 메뉴판 글씨까지 읽히고 디스크를 아끼는 크기다. */
	static final int FULL_SIZE = 1600;

	/** 썸네일의 긴 변(px). 말풍선(최대 240px)을 고해상도 화면에서도 선명하게 보여줄 수 있는 크기다. */
	static final int THUMBNAIL_SIZE = 480;

	private ChatPhotoImages() {
	}

	/** 원본·썸네일 JPEG와 원본 크기 */
	record Processed(byte[] full, byte[] thumbnail, int width, int height) {
	}

	static Processed process(byte[] bytes) {
		BufferedImage full = ImageFiles.fitWithin(ImageFiles.flatten(ImageFiles.read(bytes)), FULL_SIZE);
		BufferedImage thumbnail = ImageFiles.fitWithin(full, THUMBNAIL_SIZE);
		return new Processed(ImageFiles.encode(full), ImageFiles.encode(thumbnail), full.getWidth(), full.getHeight());
	}
}
