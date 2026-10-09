package com.ohjumwhat.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 읽은 위치 upsert(greatest)는 여러 기기가 동시에 보내도 뒤로 가지 않는다(이슈 #143, 안전 확인). */
class ChatReadConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	ChatService chatService;

	User kim;

	User lee;

	Long pollId;

	List<Long> messageIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		var org = organizationService.create(kim.getId(), "개발팀");
		inviteService.join(org.inviteToken(), lee.getId());
		pollId = pollService.create(org.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
		for (int i = 1; i <= 5; i++) {
			messageIds.add(chatService.send(pollId, lee.getId(), "메시지 " + i).id());
		}
	}

	@Test
	void 여러_기기가_동시에_다른_위치를_보내도_가장_뒤의_위치가_남는다() throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(3);
		List<Future<Void>> futures = new ArrayList<>();
		for (Long id : List.of(messageIds.get(4), messageIds.get(2), messageIds.get(3))) {
			futures.add(inThread(() -> {
				try {
					barrier.await(10, TimeUnit.SECONDS);
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
				chatService.markRead(pollId, kim.getId(), id);
			}));
		}
		for (Future<Void> future : futures) {
			await(future);
		}

		assertThat(lastRead()).isEqualTo(messageIds.get(4));

		// 늦게 온 앞쪽 위치도 뒤로 돌리지 않고, 마지막 메시지를 넘는 값은 마지막 메시지로 잘린다
		chatService.markRead(pollId, kim.getId(), messageIds.get(0));
		assertThat(lastRead()).isEqualTo(messageIds.get(4));
		chatService.markRead(pollId, kim.getId(), messageIds.get(4) + 100);
		assertThat(lastRead()).isEqualTo(messageIds.get(4));
	}

	private Long lastRead() {
		return jdbcTemplate.queryForObject("select last_read_message_id from chat_reads where poll_id = ? and user_id = ?",
				Long.class, pollId, kim.getId());
	}
}
