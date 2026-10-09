package com.ohjumwhat.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketMessage;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 채팅 브로드캐스트는 커밋 뒤 리스너(AFTER_COMMIT)가 요청 스레드에서 동기로 보낸다. 첫 보내기는 데코레이터의 버퍼를 거치지 않고
 * 소켓에 직접 쓰므로 느린 소켓이면 그 스레드가 멈춘다. Spring은 커밋 뒤 콜백을 커넥션 반납 전에 실행하므로, 그동안 Tomcat 스레드와
 * Hikari 커넥션(풀 10)이 함께 묶인다(이슈 #143, 결함 기록).
 */
class ChatHubConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	PollService pollService;

	@Autowired
	ChatService chatService;

	@Autowired
	ChatHub chatHub;

	User kim;

	Long orgId;

	Long pollId;

	BlockingSession slow;

	/** 보내기에서 풀어 줄 때까지 멈추는 연결 */
	static final class BlockingSession extends FakeWebSocketSession {

		final CountDownLatch entered = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		BlockingSession(Long pollId, Long organizationId, Long userId) {
			super(pollId, organizationId, userId, "session-slow");
		}

		@Override
		public void sendMessage(WebSocketMessage<?> message) {
			entered.countDown();
			try {
				release.await(30, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			super.sendMessage(message);
		}
	}

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		orgId = organizationService.create(kim.getId(), "개발팀").id();
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
		slow = new BlockingSession(pollId, orgId, kim.getId());
		chatHub.register(slow);
	}

	/** 허브는 컨텍스트 전체가 공유하므로 연결을 꼭 빼 둔다. */
	@AfterEach
	void unregister() {
		slow.release.countDown();
		chatHub.unregister(slow);
	}

	/**
	 * 결함 기록: 느린 소켓에 쓰는 동안 보낸 사람의 요청은 끝나지 않고 DB 커넥션도 반납되지 않는다. #143의 수정 이슈에서
	 * 「브로드캐스트를 별도 스레드(투표별 순서 유지)로 넘겨 커밋 뒤 콜백이 바로 끝난다」로 뒤집는다.
	 */
	@Test
	void 느린_소켓에_보내는_동안_요청_스레드와_DB_커넥션이_묶인다_현재_동작() throws Exception {
		int before = activeConnections();

		Future<ChatMessageResponse> sender = inThread(() -> chatService.send(pollId, kim.getId(), "안녕하세요"));
		assertThat(slow.entered.await(10, TimeUnit.SECONDS)).isTrue();

		// 소켓에 쓰는 중: 메시지는 이미 커밋됐지만 요청은 끝나지 않았고 커넥션은 빌린 채다
		assertThat(jdbcTemplate.queryForObject("select count(*) from chat_messages", Long.class)).isEqualTo(1);
		assertThat(sender.isDone()).isFalse();
		assertThat(activeConnections()).isEqualTo(before + 1);

		slow.release.countDown();
		await(sender);
		awaitTrue("커넥션 반납", () -> activeConnections() == before);
		assertThat(slow.texts()).hasSize(1);
	}
}
