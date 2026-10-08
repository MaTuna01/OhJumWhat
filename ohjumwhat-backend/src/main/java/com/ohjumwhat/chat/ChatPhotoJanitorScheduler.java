package com.ohjumwhat.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:00(KST)에 채팅 사진을 정리한다. 스케줄러 스레드는 정기 투표(매분)와 함께 쓰므로 한가한 새벽에 돈다.
 * 테스트에서는 ohjumwhat.scheduler.enabled=false로 끄고 {@link ChatPhotoJanitor}를 직접 호출한다.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ohjumwhat.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ChatPhotoJanitorScheduler {

	private final ChatPhotoJanitor janitor;

	ChatPhotoJanitorScheduler(ChatPhotoJanitor janitor) {
		this.janitor = janitor;
	}

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	void clean() {
		janitor.clean();
	}
}
