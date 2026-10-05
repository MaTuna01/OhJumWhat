package com.ohjumwhat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ohjumwhat.chat.ChatRateLimiter;
import com.ohjumwhat.letter.LetterRateLimiter;
import com.ohjumwhat.user.ProfilePhotoStorage;

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 스프링 컨텍스트와 PostgreSQL 컨테이너를 공유하고,
 * 테스트가 끝날 때마다 테이블과 프로필 사진 폴더를 비우고 시계를 실제 시각으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeNaverShortLinksConfiguration.class,
		FakeKakaoLocalConfiguration.class })
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected TestClock clock;

	@Autowired
	protected ProfilePhotoStorage photoStorage;

	@Autowired
	private ChatRateLimiter chatRateLimiter;

	@Autowired
	private LetterRateLimiter letterRateLimiter;

	@AfterEach
	void cleanDatabase() throws IOException {
		clock.reset();
		chatRateLimiter.clear();
		letterRateLimiter.clear();
		jdbcTemplate.execute("""
				TRUNCATE users, organizations, memberships, poll_schedules, polls, menu_options, menu_comments, votes,
					chat_messages, chat_reads, spring_session, blocked_accounts, notices, letters, letter_blocks, letter_reports
				RESTART IDENTITY CASCADE""");
		try (Stream<Path> files = Files.list(photoStorage.dir())) {
			for (Path file : files.toList()) {
				Files.delete(file);
			}
		}
	}

	/** 프로필 사진 폴더의 파일 이름(정렬) */
	protected List<String> photoFiles() throws IOException {
		try (Stream<Path> files = Files.list(photoStorage.dir())) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}
}
