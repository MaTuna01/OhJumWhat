package com.ohjumwhat.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.ApiException;

/** 메시지 보내기 속도 제한: 한 사람이 10초에 10개까지(서버 메모리, 앱이 하나라 충분하다). */
@Component
public class ChatRateLimiter {

	static final int LIMIT = 10;

	static final Duration WINDOW = Duration.ofSeconds(10);

	private final Map<Long, Deque<Instant>> sent = new ConcurrentHashMap<>();

	private final Clock clock;

	public ChatRateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** 보낼 수 있으면 기록하고, 너무 빠르면 429 */
	void acquire(Long userId) {
		Instant now = Instant.now(clock);
		Deque<Instant> times = sent.computeIfAbsent(userId, id -> new ArrayDeque<>());
		synchronized (times) {
			dropOld(times, now);
			if (times.size() >= LIMIT) {
				throw ApiException.tooManyRequests("메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요.");
			}
			times.addLast(now);
		}
	}

	/** 오래된 기록을 지운다(주기적으로 호출). */
	void prune() {
		Instant now = Instant.now(clock);
		sent.values().removeIf(times -> {
			synchronized (times) {
				dropOld(times, now);
				return times.isEmpty();
			}
		});
	}

	/** 테스트: 테스트마다 기록을 비운다(회원 ID가 테스트마다 1부터 다시 시작한다). */
	public void clear() {
		sent.clear();
	}

	private static void dropOld(Deque<Instant> times, Instant now) {
		Instant from = now.minus(WINDOW);
		while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
			times.pollFirst();
		}
	}
}
