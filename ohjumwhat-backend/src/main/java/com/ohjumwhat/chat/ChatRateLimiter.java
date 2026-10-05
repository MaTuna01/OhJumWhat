package com.ohjumwhat.chat;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.SlidingWindowRateLimiter;

/** 메시지 보내기 속도 제한: 한 사람이 10초에 10개까지(서버 메모리, 앱이 하나라 충분하다). */
@Component
public class ChatRateLimiter {

	static final int LIMIT = 10;

	static final Duration WINDOW = Duration.ofSeconds(10);

	private final SlidingWindowRateLimiter limiter;

	public ChatRateLimiter(Clock clock) {
		this.limiter = new SlidingWindowRateLimiter(LIMIT, WINDOW, "메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요.",
				clock);
	}

	/** 보낼 수 있으면 기록하고, 너무 빠르면 429 */
	void acquire(Long userId) {
		limiter.acquire(userId);
	}

	/** 오래된 기록을 지운다(주기적으로 호출). */
	void prune() {
		limiter.prune();
	}

	/** 테스트: 테스트마다 기록을 비운다(회원 ID가 테스트마다 1부터 다시 시작한다). */
	public void clear() {
		limiter.clear();
	}
}
