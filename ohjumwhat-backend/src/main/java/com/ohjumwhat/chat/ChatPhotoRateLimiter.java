package com.ohjumwhat.chat;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.SlidingWindowRateLimiter;

/**
 * 사진 보내기 속도 제한: 한 사람이 10분에 20장까지(서버 메모리). 사진은 다시 그리느라 CPU·메모리·디스크를 쓰므로
 * 메시지 속도 제한({@link ChatRateLimiter}, 10초에 10개)과 함께 건다.
 */
@Component
public class ChatPhotoRateLimiter {

	static final int LIMIT = 20;

	static final Duration WINDOW = Duration.ofMinutes(10);

	private final SlidingWindowRateLimiter limiter;

	public ChatPhotoRateLimiter(Clock clock) {
		this.limiter = new SlidingWindowRateLimiter(LIMIT, WINDOW, "사진을 너무 많이 보내고 있어요. 잠시 후 다시 보내 주세요.",
				clock);
	}

	/** 보낼 수 있으면 기록하고, 너무 많으면 429 */
	void acquire(Long userId) {
		limiter.acquire(userId);
	}

	/** 오래된 기록을 지운다(주기적으로 호출). */
	void prune() {
		limiter.prune();
	}

	/** 테스트: 테스트마다 기록을 비운다. */
	public void clear() {
		limiter.clear();
	}
}
