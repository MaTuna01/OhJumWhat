package com.ohjumwhat.chat;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 30초마다 채팅 연결을 점검한다. 테스트에서는 ohjumwhat.scheduler.enabled=false로 끄고 {@link ChatSweeper}를 직접 호출한다. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ohjumwhat.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ChatSweepScheduler {

	private final ChatSweeper sweeper;

	ChatSweepScheduler(ChatSweeper sweeper) {
		this.sweeper = sweeper;
	}

	@Scheduled(fixedDelay = 30, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
	void sweep() {
		sweeper.sweep();
	}
}
