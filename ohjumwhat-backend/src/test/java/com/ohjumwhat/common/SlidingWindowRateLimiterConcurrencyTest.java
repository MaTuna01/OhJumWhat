package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** 속도 제한기는 compute 안에서 사람별로 직렬화되므로 동시에 몰려도 한도를 정확히 지킨다(이슈 #143, 안전 확인). */
class SlidingWindowRateLimiterConcurrencyTest {

	@Test
	void 같은_사람의_동시_요청_100개_중_정확히_10개만_허용한다() throws Exception {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10, Duration.ofSeconds(10), "너무 잦아요.",
				Clock.fixed(Instant.parse("2026-09-30T02:00:00Z"), ZoneOffset.UTC));
		int threads = 100;
		CyclicBarrier barrier = new CyclicBarrier(threads);
		AtomicInteger allowed = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					barrier.await(10, TimeUnit.SECONDS);
					try {
						limiter.acquire(1L);
						allowed.incrementAndGet();
					}
					catch (ApiException e) {
						assertThat(e.getMessage()).isEqualTo("너무 잦아요.");
						rejected.incrementAndGet();
					}
					return null;
				}));
			}
			for (Future<?> future : futures) {
				future.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(allowed.get()).isEqualTo(10);
		assertThat(rejected.get()).isEqualTo(90);
		// 다른 사람은 영향을 받지 않는다
		limiter.acquire(2L);
	}
}
