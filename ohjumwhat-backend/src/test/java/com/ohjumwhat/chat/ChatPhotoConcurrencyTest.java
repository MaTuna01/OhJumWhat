package com.ohjumwhat.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.TestImages;
import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 채팅 사진은 동시에 2장만 다시 그리고(세마포어), 2초 안에 자리가 나지 않으면 503이다. 열 사람이 한꺼번에 보내면 몇 장이 503인지는
 * 사진 한 장을 그리는 시간에 달려 있어 수치만 기록한다(이슈 #143). 성공 + 503 = 전부여야 하고, 적어도 2장은 성공한다.
 */
class ChatPhotoConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	ChatPhotoService chatPhotoService;

	List<User> members = new ArrayList<>();

	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		User kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		members.add(kim);
		var org = organizationService.create(kim.getId(), "개발팀");
		for (int i = 1; i < 10; i++) {
			User member = userRepository.save(new User("sub-" + i, i + "@example.com", "멤버 " + i, null));
			inviteService.join(org.inviteToken(), member.getId());
			members.add(member);
		}
		pollId = pollService.create(org.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@Test
	void 열_사람이_동시에_보내면_세마포어_밖의_사진은_503이고_나머지는_저장된다() throws Exception {
		byte[] photo = TestImages.jpeg(2048, 1536);
		CyclicBarrier barrier = new CyclicBarrier(members.size());
		AtomicInteger ok = new AtomicInteger();
		AtomicInteger busy = new AtomicInteger();
		List<Future<Void>> futures = new ArrayList<>();
		long started = System.nanoTime();
		for (User member : members) {
			futures.add(inThread(() -> {
				try {
					barrier.await(10, TimeUnit.SECONDS);
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
				try {
					chatPhotoService.send(pollId, member.getId(),
							new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photo));
					ok.incrementAndGet();
				}
				catch (ApiException e) {
					assertThat(e.getMessage()).contains("잠시 후");
					busy.incrementAndGet();
				}
			}));
		}
		for (Future<Void> future : futures) {
			try {
				future.get(60, TimeUnit.SECONDS);
			}
			catch (ExecutionException e) {
				throw new AssertionError(e.getCause());
			}
		}
		long elapsedMs = (System.nanoTime() - started) / 1_000_000;

		System.out.printf("채팅 사진 동시 10장: 성공 %d, 503 %d, %dms%n", ok.get(), busy.get(), elapsedMs);
		assertThat(ok.get() + busy.get()).isEqualTo(members.size());
		assertThat(ok.get()).isGreaterThanOrEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select count(*) from chat_messages where image_key is not null", Long.class))
			.isEqualTo(ok.get());
	}
}
