package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;
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
				e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
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

	@Test
	void 한도가_1이면_기간이_꼭_지나야_다시_되고_막힌_시도는_세지_않는다() {
		SlidingWindowRateLimiter once = new SlidingWindowRateLimiter(1, Duration.ofSeconds(5), "잠시 후에 다시 해 주세요.",
				clock);
		once.acquire(1L);

		clock.now = clock.now.plusSeconds(4);
		assertThatThrownBy(() -> once.acquire(1L)).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(e.getMessage()).isEqualTo("잠시 후에 다시 해 주세요.");
		});
		// 다른 사람은 따로 센다.
		assertThatCode(() -> once.acquire(2L)).doesNotThrowAnyException();

		// 막힌 시도(4초)는 기록하지 않아, 처음 한 때부터 꼭 5초가 지나면 된다.
		clock.now = clock.now.plusSeconds(1);
		assertThatCode(() -> once.acquire(1L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> once.acquire(1L)).isInstanceOf(ApiException.class);
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
