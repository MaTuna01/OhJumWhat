package com.ohjumwhat.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	/** 서버 시간대와 관계없이 "오늘"과 정기 투표 시간은 한국 시간으로 계산한다. */
	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Bean
	Clock clock() {
		return Clock.system(KST);
	}
}
