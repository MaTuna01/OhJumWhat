package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.Organization;
import com.ohjumwhat.organization.OrganizationRepository;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.schedule.PollSchedule;
import com.ohjumwhat.schedule.PollScheduleRepository;

/**
 * DB에 저장되는 날짜·시각 원본 값을 확인한다.
 * poll_date(DATE)와 open/close_time(TIME)은 한국 기준 값 그대로, opens/closes_at(TIMESTAMPTZ)은 정확한 시점으로 저장돼야 한다.
 */
class TimeStorageTest extends IntegrationTest {

	@Autowired
	OrganizationRepository organizationRepository;

	@Autowired
	PollScheduleRepository scheduleRepository;

	@Autowired
	PollRepository pollRepository;

	@Test
	void 날짜와_시각은_한국_기준_값_그대로_저장된다() {
		Long orgId = organizationRepository.save(new Organization("개발팀", "token")).getId();
		scheduleRepository.save(new PollSchedule(orgId, "아침", 31, LocalTime.of(8, 0), LocalTime.of(12, 0)));
		pollRepository.save(Poll.scheduled(orgId, null, "점심", LocalDate.of(2026, 9, 30),
				Instant.parse("2026-09-30T02:00:00Z"), Instant.parse("2026-09-30T02:50:00Z")));

		assertThat(jdbcTemplate.queryForObject("select open_time::text || '-' || close_time::text from poll_schedules",
				String.class)).isEqualTo("08:00:00-12:00:00");
		assertThat(jdbcTemplate.queryForObject("select poll_date::text from polls", String.class)).isEqualTo("2026-09-30");
		assertThat(jdbcTemplate.queryForObject(
				"select to_char(opens_at at time zone 'Asia/Seoul', 'YYYY-MM-DD HH24:MI') from polls", String.class))
			.isEqualTo("2026-09-30 11:00");
	}
}
