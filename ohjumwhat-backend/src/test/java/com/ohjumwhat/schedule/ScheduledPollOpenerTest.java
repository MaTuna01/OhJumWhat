package com.ohjumwhat.schedule;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 2026-09-30은 수요일이다. */
class ScheduledPollOpenerTest extends IntegrationTest {

	private static final int WEEKDAYS = 31;

	private static final int FRIDAY = 16;

	@Autowired
	ScheduledPollOpener opener;

	@Autowired
	PollScheduleRepository scheduleRepository;

	@Autowired
	PollRepository pollRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	User kim;

	Long orgId;

	@BeforeEach
	void setUp() {
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		orgId = organizationService.create(kim.getId(), "개발팀").id();
	}

	@Test
	void 오픈_시각이_되면_투표를_연다() throws Exception {
		PollSchedule lunch = schedule("점심", WEEKDAYS, "11:00", "11:50");

		clock.set(2026, 9, 30, 10, 59);
		assertThat(opener.openDuePolls()).isZero();

		clock.set(2026, 9, 30, 11, 0);
		assertThat(opener.openDuePolls()).isEqualTo(1);

		Poll poll = pollRepository.findAll().getFirst();
		assertThat(poll.getTitle()).isEqualTo("점심");
		assertThat(poll.getScheduleId()).isEqualTo(lunch.getId());
		assertThat(poll.getCreatedBy()).isNull();
		assertThat(poll.getPollDate()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(poll.getOpensAt()).isEqualTo(Instant.parse("2026-09-30T02:00:00Z"));
		assertThat(poll.getClosesAt()).isEqualTo(Instant.parse("2026-09-30T02:50:00Z"));

		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + poll.getId()).with(loginAs(kim)))
			.andExpect(jsonPath("$.scheduled").value(true))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void 여러_번_실행해도_하루에_하나만_연다() {
		schedule("점심", WEEKDAYS, "11:00", "11:50");
		clock.set(2026, 9, 30, 11, 0);
		opener.openDuePolls();
		clock.set(2026, 9, 30, 11, 1);
		assertThat(opener.openDuePolls()).isZero();
		clock.set(2026, 9, 30, 11, 49);
		assertThat(opener.openDuePolls()).isZero();

		assertThat(pollRepository.count()).isEqualTo(1);
	}

	@Test
	void 규칙에_없는_요일에는_열지_않는다() {
		schedule("금요 회식", FRIDAY, "17:00", "17:40");

		clock.set(2026, 9, 30, 17, 10); // 수요일
		assertThat(opener.openDuePolls()).isZero();

		clock.set(2026, 10, 2, 17, 10); // 금요일
		assertThat(opener.openDuePolls()).isEqualTo(1);
	}

	@Test
	void 마감_시각_이후에는_열지_않는다() {
		schedule("점심", WEEKDAYS, "11:00", "11:50");

		clock.set(2026, 9, 30, 11, 50);
		assertThat(opener.openDuePolls()).isZero();
	}

	@Test
	void 서버가_오픈_시각에_꺼져_있었어도_마감_전에_켜지면_그날_투표를_연다() {
		schedule("점심", WEEKDAYS, "11:00", "11:50");

		clock.set(2026, 9, 30, 11, 20);
		assertThat(opener.openDuePolls()).isEqualTo(1);
		assertThat(pollRepository.findAll().getFirst().getOpensAt()).isEqualTo(Instant.parse("2026-09-30T02:00:00Z"));
	}

	@Test
	void 규칙마다_따로_열고_다음_날에는_새로_연다() {
		schedule("아침", WEEKDAYS, "08:00", "12:00");
		schedule("점심", WEEKDAYS, "11:00", "11:50");

		clock.set(2026, 9, 30, 11, 10);
		assertThat(opener.openDuePolls()).isEqualTo(2);

		clock.set(2026, 10, 1, 11, 10);
		assertThat(opener.openDuePolls()).isEqualTo(2);

		List<LocalDate> dates = pollRepository.findAll().stream().map(Poll::getPollDate).distinct().sorted().toList();
		assertThat(dates).containsExactly(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
	}

	private PollSchedule schedule(String name, int days, String open, String close) {
		return scheduleRepository.save(new PollSchedule(orgId, name, days, LocalTime.parse(open), LocalTime.parse(close)));
	}
}
