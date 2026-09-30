package com.ohjumwhat.user;

import static org.assertj.core.api.Assertions.assertThat;

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
}
