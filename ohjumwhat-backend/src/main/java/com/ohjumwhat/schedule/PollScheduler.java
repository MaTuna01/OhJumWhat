package com.ohjumwhat.schedule;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매분 0초(한국 시간)에 정기 투표를 연다.
 * 테스트에서는 ohjumwhat.scheduler.enabled=false로 끄고 {@link ScheduledPollOpener}를 직접 호출한다.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ohjumwhat.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PollScheduler {

	private final ScheduledPollOpener opener;

	public PollScheduler(ScheduledPollOpener opener) {
		this.opener = opener;
	}

	@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
	void openDuePolls() {
		opener.openDuePolls();
	}
}
