package com.ohjumwhat.letter;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.SlidingWindowRateLimiter;

/** 쪽지 보내기 속도 제한: 한 사람이 10분에 10통까지(답장 포함). 기록은 그 사람이 보낼 때 함께 정리된다. */
@Component
public class LetterRateLimiter {

	static final int LIMIT = 10;

	static final Duration WINDOW = Duration.ofMinutes(10);

	private final SlidingWindowRateLimiter limiter;

	public LetterRateLimiter(Clock clock) {
		this.limiter = new SlidingWindowRateLimiter(LIMIT, WINDOW, "쪽지를 너무 많이 보냈어요. 잠시 후 다시 보내 주세요.", clock);
	}

	void acquire(Long userId) {
		limiter.acquire(userId);
	}

	/** 테스트: 테스트마다 기록을 비운다. */
	public void clear() {
		limiter.clear();
	}
}
