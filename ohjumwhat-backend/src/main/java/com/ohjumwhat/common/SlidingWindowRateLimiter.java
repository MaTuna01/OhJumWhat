package com.ohjumwhat.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한 사람이 기간 안에 limit번까지만 하게 하는 속도 제한(서버 메모리, 앱이 하나라 충분하다).
 * 채팅(ChatRateLimiter)과 쪽지(LetterRateLimiter)가 각자 하나씩 둔다.
 */
public class SlidingWindowRateLimiter {

	private final int limit;

	private final Duration window;

	private final String message;

	private final Clock clock;

	private final Map<Long, Deque<Instant>> times = new ConcurrentHashMap<>();

	/** @param message 한도를 넘었을 때 429 문구 */
	public SlidingWindowRateLimiter(int limit, Duration window, String message, Clock clock) {
		this.limit = limit;
		this.window = window;
		this.message = message;
		this.clock = clock;
	}

	/** 할 수 있으면 기록하고, 너무 잦으면 429 */
	public void acquire(Long userId) {
		Instant now = Instant.now(clock);
		boolean[] allowed = { false };
		// 기록을 compute 안에서 바꿔 prune이 같은 사람의 기록을 맵에서 빼는 것과 겹치지 않게 한다.
		times.compute(userId, (id, userTimes) -> {
			Deque<Instant> recent = userTimes == null ? new ArrayDeque<>() : userTimes;
			dropOld(recent, now);
			if (recent.size() < limit) {
				recent.addLast(now);
				allowed[0] = true;
			}
			return recent.isEmpty() ? null : recent;
		});
		if (!allowed[0]) {
			throw ApiException.tooManyRequests(message);
		}
	}

	/** 기간이 지난 기록을 지운다(주기적으로 호출). */
	public void prune() {
		Instant now = Instant.now(clock);
		for (Long userId : times.keySet()) {
			times.computeIfPresent(userId, (id, userTimes) -> {
				dropOld(userTimes, now);
				return userTimes.isEmpty() ? null : userTimes;
			});
		}
	}

	/** 테스트: 기록을 비운다(회원 ID가 테스트마다 1부터 다시 시작한다). */
	public void clear() {
		times.clear();
	}

	private void dropOld(Deque<Instant> userTimes, Instant now) {
		Instant from = now.minus(window);
		while (!userTimes.isEmpty() && !userTimes.peekFirst().isAfter(from)) {
			userTimes.pollFirst();
		}
	}
}
