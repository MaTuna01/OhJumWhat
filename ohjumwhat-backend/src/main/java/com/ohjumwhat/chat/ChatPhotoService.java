package com.ohjumwhat.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ohjumwhat.common.ApiException;

/**
 * 채팅 사진 보내기. 확인 → 속도 제한 → 읽기 → 다시 그리기 → 파일 쓰기는 트랜잭션 밖에서 하고, 메시지 저장만
 * {@link ChatService#sendPhoto}가 한다. 저장하지 못하면 방금 쓴 파일을 지운다.
 * 2048px 그림 하나를 풀면 16MB쯤이라 동시에 두 장까지만 그린다(메모리가 넘치면 앱이 재시작해 채팅 연결이 모두 끊긴다).
 */
@Service
public class ChatPhotoService {

	private static final int CONCURRENCY = 2;

	private static final long WAIT_SECONDS = 2;

	private final ChatService chatService;

	private final ChatPhotoStorage storage;

	private final ChatRateLimiter rateLimiter;

	private final ChatPhotoRateLimiter photoRateLimiter;

	private final Semaphore drawing = new Semaphore(CONCURRENCY);

	public ChatPhotoService(ChatService chatService, ChatPhotoStorage storage, ChatRateLimiter rateLimiter,
			ChatPhotoRateLimiter photoRateLimiter) {
		this.chatService = chatService;
		this.storage = storage;
		this.rateLimiter = rateLimiter;
		this.photoRateLimiter = photoRateLimiter;
	}

	/** 올린 파일(최대 5MB)은 멤버·채팅 기간·속도 제한을 확인한 뒤에 메모리로 읽는다. */
	public ChatMessageResponse send(Long pollId, Long userId, MultipartFile file) {
		chatService.requireCanSend(pollId, userId);
		rateLimiter.acquire(userId);
		photoRateLimiter.acquire(userId);
		ChatPhotoImages.Processed photo = draw(read(file));
		String key = storage.write(photo);
		try {
			return chatService.sendPhoto(pollId, userId, key, photo.width(), photo.height());
		}
		catch (RuntimeException e) {
			storage.delete(key);
			throw e;
		}
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException("올린 사진을 읽지 못했습니다.", e);
		}
	}

	private ChatPhotoImages.Processed draw(byte[] bytes) {
		boolean acquired = false;
		try {
			acquired = drawing.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS);
			if (!acquired) {
				throw ApiException.unavailable("사진을 보내는 사람이 많아요. 잠시 뒤 다시 보내 주세요.");
			}
			return ChatPhotoImages.process(bytes);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw ApiException.unavailable("사진을 보내는 사람이 많아요. 잠시 뒤 다시 보내 주세요.");
		}
		finally {
			if (acquired) {
				drawing.release();
			}
		}
	}
}
