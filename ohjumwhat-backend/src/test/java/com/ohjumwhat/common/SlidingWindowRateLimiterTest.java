package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SlidingWindowRateLimiterTest {

	final MutableClock clock = new MutableClock();

	final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofMinutes(10), "너무 많아요.",
			clock);

	@Test
	void 기간_안에_한도까지만_하고_기간이_지나면_다시_된다() {
		limiter.acquire(1L);
		limiter.acquire(1L);
		assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOfSatisfying(ApiException.class,
				e -> org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
		// 다른 사람은 따로 센다.
		assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();

		clock.now = clock.now.plus(Duration.ofMinutes(10));
		assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
	}

	@Test
	void 정리하면_기간이_지난_기록이_사라진다() {
		limiter.acquire(1L);
		limiter.acquire(1L);
		clock.now = clock.now.plus(Duration.ofMinutes(11));
		limiter.prune();
		assertThatCode(() -> {
			limiter.acquire(1L);
			limiter.acquire(1L);
		}).doesNotThrowAnyException();
	}

	static class MutableClock extends Clock {

		Instant now = Instant.parse("2026-10-05T03:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
