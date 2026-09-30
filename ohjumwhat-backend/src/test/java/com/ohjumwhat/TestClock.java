package com.ohjumwhat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.ohjumwhat.common.TimeConfig;

/**
 * 테스트용 시계. 기본은 실제 시각이고, {@link #set}으로 특정 시각에 고정할 수 있다.
 * 테스트가 끝날 때마다 {@link IntegrationTest}가 실제 시각으로 되돌린다.
 */
public class TestClock extends Clock {

	private volatile Instant fixed;

	/** 한국 시간 기준 날짜·시각으로 고정한다. */
	public void set(int year, int month, int day, int hour, int minute) {
		this.fixed = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TimeConfig.KST).toInstant();
	}

	public void set(Instant instant) {
		this.fixed = instant;
	}

	public void reset() {
		this.fixed = null;
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
	public Instant instant() {
		Instant f = fixed;
		return f != null ? f : Instant.now();
	}
}
