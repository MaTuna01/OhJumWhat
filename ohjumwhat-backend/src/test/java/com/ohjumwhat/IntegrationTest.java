package com.ohjumwhat;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.FakePushSenderConfiguration.FakePushSender;
import com.ohjumwhat.FakePushSenderConfiguration.QueuedPushDispatcher;
import com.ohjumwhat.chat.ChatRateLimiter;
import com.ohjumwhat.guestbook.GuestbookRateLimiter;
import com.ohjumwhat.letter.LetterRateLimiter;
import com.ohjumwhat.user.ProfilePhotoStorage;
import com.ohjumwhat.user.User;

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 스프링 컨텍스트와 PostgreSQL 컨테이너를 공유하고,
 * 테스트가 끝날 때마다 테이블과 프로필 사진 폴더를 비우고 시계를 실제 시각으로 되돌리고 가짜 푸시를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeNaverShortLinksConfiguration.class,
		FakeKakaoLocalConfiguration.class, FakePushSenderConfiguration.class })
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
	protected FakePushSender pushSender;

	@Autowired
	protected QueuedPushDispatcher pushDispatcher;

	@Autowired
	private ChatRateLimiter chatRateLimiter;

	@Autowired
	private LetterRateLimiter letterRateLimiter;

	@Autowired
	private GuestbookRateLimiter guestbookRateLimiter;

	@Autowired
	private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	@AfterEach
	void cleanDatabase() throws IOException {
		clock.reset();
		chatRateLimiter.clear();
		letterRateLimiter.clear();
		guestbookRateLimiter.clear();
		pushDispatcher.clear();
		pushSender.reset();
		jdbcTemplate.execute("""
				TRUNCATE users, organizations, memberships, poll_schedules, polls, menu_options, menu_comments, votes,
					chat_messages, chat_reads, spring_session, blocked_accounts, notices, letters, letter_blocks, letter_reports,
					guestbook_entries, guestbook_reports, push_devices, user_sanctions, profile_reports
				RESTART IDENTITY CASCADE""");
		try (Stream<Path> files = Files.list(photoStorage.dir())) {
			for (Path file : files.toList()) {
				Files.delete(file);
			}
		}
	}

	/**
	 * 그 회원의 로그인 세션을 DB(spring_session)에 만들고 세션 ID를 준다. principal 이름은 실제 로그인처럼 google sub다
	 * (강제 탈퇴가 이것으로 세션을 지운다). MockMvc의 loginAs는 보안 컨텍스트만 넣고 세션 행은 만들지 않는다.
	 */
	protected String loginSession(User user) {
		return createSession(sessionRepository, user.getGoogleSub());
	}

	/** 그 로그인 세션으로 이 기기(FID)에서 알림 받기를 켠다. */
	protected ResultActions registerPushDevice(User user, String sessionId, String fid) throws Exception {
		return mockMvc.perform(put("/api/push/devices/{fid}", fid).with(loginAs(user)).with(xsrf(sessionId)));
	}

	private static <S extends Session> String createSession(FindByIndexNameSessionRepository<S> repository,
			String principalName) {
		S session = repository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
		repository.save(session);
		return session.getId();
	}

	/** 프로필 사진 폴더의 파일 이름(정렬) */
	protected List<String> photoFiles() throws IOException {
		try (Stream<Path> files = Files.list(photoStorage.dir())) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}
}
