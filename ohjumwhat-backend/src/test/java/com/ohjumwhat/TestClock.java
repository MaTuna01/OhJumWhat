package com.ohjumwhat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.ohjumwhat.common.TimeConfig;

/**
 * 테스트용 시계. 기본은 실제 시각이고, {@link #set}으로 특정 시각에 고정할 수 있다.
 * 테스트가 끝날 때마다 {@link IntegrationTest}가 실제 시각으로 되돌린다.
 */
public class TestClock extends Clock {

	private Instant fixed;

	/** null이 아니면 읽을 때마다 이만큼 앞으로 간다({@link #tick}). */
	private Duration step;

	/** 한국 시간 기준 날짜·시각으로 고정한다. */
	public void set(int year, int month, int day, int hour, int minute) {
		set(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TimeConfig.KST).toInstant());
	}

	public synchronized void set(Instant instant) {
		this.fixed = instant;
		this.step = null;
	}

	/**
	 * start에서 시작해 읽을 때마다 step만큼 앞으로 가는 시계. 한 요청에서 시계를 두 번 읽는 코드가
	 * 자정 같은 경계에서 서로 다른 날짜를 얻는지 확인할 때 쓴다.
	 */
	public synchronized void tick(Instant start, Duration step) {
		this.fixed = start;
		this.step = step;
	}

	public synchronized void reset() {
		this.fixed = null;
		this.step = null;
	}

	@Override
	public ZoneId getZone() {
		return TimeConfig.KST;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Instant instant() {
		Instant f = fixed;
		if (f == null) {
			return Instant.now();
		}
		if (step != null) {
			fixed = f.plus(step);
		}
		return f;
	}
}
