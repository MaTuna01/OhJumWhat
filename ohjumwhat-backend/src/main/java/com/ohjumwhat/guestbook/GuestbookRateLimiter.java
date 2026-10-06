package com.ohjumwhat.guestbook;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.SlidingWindowRateLimiter;

/** 방명록 도배 방지: 쓴 사람 한 명이 모든 방명록을 합쳐 5초에 한 번. 기록은 그 사람이 쓸 때 함께 정리된다. */
@Component
public class GuestbookRateLimiter {

	static final Duration WINDOW = Duration.ofSeconds(5);

	private final SlidingWindowRateLimiter limiter;

	public GuestbookRateLimiter(Clock clock) {
		this.limiter = new SlidingWindowRateLimiter(1, WINDOW, "방명록은 5초에 한 번 남길 수 있어요. 잠시 후 다시 남겨 주세요.",
				clock);
	}

	void acquire(Long userId) {
		limiter.acquire(userId);
	}

	/** 테스트: 테스트마다 기록을 비운다. */
	public void clear() {
		limiter.clear();
	}
}
