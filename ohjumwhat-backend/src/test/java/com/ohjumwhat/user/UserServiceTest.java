package com.ohjumwhat.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;

class UserServiceTest extends IntegrationTest {

	@Autowired
	UserService userService;

	@Autowired
	UserRepository userRepository;

	@Test
	void 처음_로그인하면_사용자를_만들고_다시_로그인하면_프로필을_갱신한다() {
		User created = userService.upsertGoogleUser("sub-1", "kim@example.com", "김철수", "https://img/1");
		User updated = userService.upsertGoogleUser("sub-1", "kim2@example.com", "김철수2", null);

		assertThat(updated.getId()).isEqualTo(created.getId());
		assertThat(userRepository.count()).isEqualTo(1);
		User saved = userRepository.findById(created.getId()).orElseThrow();
		assertThat(saved.getEmail()).isEqualTo("kim2@example.com");
		assertThat(saved.getName()).isEqualTo("김철수2");
		assertThat(saved.getProfileImageUrl()).isNull();
	}

	@Test
	void 구글_이름이_없으면_이메일_앞부분을_이름으로_쓴다() {
		User user = userService.upsertGoogleUser("sub-1", "lee@example.com", "  ", null);

		assertThat(user.getName()).isEqualTo("lee");
	}

	@Test
	void 로그인하면_최근_로그인_시각을_남긴다() {
		clock.set(2026, 9, 30, 9, 0);

		User user = userService.login("sub-1", "kim@example.com", true, "김철수", null);

		assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt())
			.isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
	}

	@Test
	void 관리자_이메일로_인증된_구글_로그인을_하면_관리자가_된다() {
		User admin = userService.login("sub-admin", "Admin@Example.com", true, "관리자", null);
		User unverified = userService.login("sub-fake", "admin@example.com", false, "가짜", null);
		User normal = userService.login("sub-kim", "kim@example.com", true, "김철수", null);

		assertThat(userRepository.findById(admin.getId()).orElseThrow().isAdmin()).isTrue();
		assertThat(userRepository.findById(unverified.getId()).orElseThrow().isAdmin()).isFalse();
		assertThat(userRepository.findById(normal.getId()).orElseThrow().isAdmin()).isFalse();
	}

	@Test
	void 서버_시작_때_관리자_목록을_설정과_맞춘다() {
		User listed = userRepository.save(new User("sub-admin", "admin@example.com", "관리자", null));
		User former = new User("sub-old", "old@example.com", "예전 관리자", null);
		former.promote();
		former = userRepository.save(former);

		userService.syncAdmins();

		assertThat(userRepository.findById(listed.getId()).orElseThrow().isAdmin()).isTrue();
		assertThat(userRepository.findById(former.getId()).orElseThrow().isAdmin()).isFalse();
	}
}
