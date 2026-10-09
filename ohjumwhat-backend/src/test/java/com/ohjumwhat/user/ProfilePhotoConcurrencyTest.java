package com.ohjumwhat.user;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ohjumwhat.ConcurrencyTest;
import com.ohjumwhat.TestImages;

/**
 * 프로필 사진 올리기에는 채팅 사진과 달리 동시 처리 제한이 없다. 2048px 사진 열 장을 동시에 올리면 모두 성공하고, 그동안 힙이
 * 얼마나 늘어나는지 기록한다(이슈 #143). 운영은 MaxRAMPercentage=50 + ExitOnOutOfMemoryError라 OOM이면 앱이 재시작된다.
 */
class ProfilePhotoConcurrencyTest extends ConcurrencyTest {

	@Autowired
	UserRepository userRepository;

	List<User> users = new ArrayList<>();

	@BeforeEach
	void setUp() {
		for (int i = 0; i < 10; i++) {
			users.add(userRepository.save(new User("sub-" + i, i + "@example.com", "회원 " + i, null)));
		}
	}

	@Test
	void 열_사람이_동시에_2048px_사진을_올리면_모두_성공한다_힙_기록() throws Exception {
		byte[] photo = TestImages.jpeg(2048, 2048);
		List<MemoryPoolMXBean> heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
			.filter(pool -> pool.getType() == MemoryType.HEAP)
			.toList();
		heapPools.forEach(MemoryPoolMXBean::resetPeakUsage);
		long usedBefore = heapPools.stream().mapToLong(pool -> pool.getUsage().getUsed()).sum();

		CyclicBarrier barrier = new CyclicBarrier(users.size());
		List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
		for (User user : users) {
			futures.add(inThread(() -> {
				barrier.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(multipart("/api/me/photo").file("photo", photo).with(loginAs(user)).with(xsrf()))
					.andReturn()
					.getResponse();
			}));
		}
		for (Future<MockHttpServletResponse> future : futures) {
			assertThat(future.get(60, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
		}

		long peak = heapPools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
		System.out.printf("프로필 사진 동시 10장(2048px): 힙 %dMB → 최대 %dMB%n", usedBefore >> 20, peak >> 20);
		assertThat(jdbcTemplate.queryForObject("select count(*) from users where photo_key is not null", Long.class))
			.isEqualTo(10);
		assertThat(photoFiles()).hasSize(10);
	}
}
