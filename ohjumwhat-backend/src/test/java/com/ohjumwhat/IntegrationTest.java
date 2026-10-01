package com.ohjumwhat;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 스프링 컨텍스트와 PostgreSQL 컨테이너를 공유하고,
 * 테스트가 끝날 때마다 테이블을 비우고 시계를 실제 시각으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected TestClock clock;

	@AfterEach
	void cleanDatabase() {
		clock.reset();
		jdbcTemplate.execute("""
				TRUNCATE users, organizations, memberships, poll_schedules, polls, menu_options, votes, spring_session,
					blocked_accounts, notices
				RESTART IDENTITY CASCADE""");
	}
}
