package com.ohjumwhat.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ohjumwhat.common.TimeConfig;
import com.ohjumwhat.poll.Poll;
import com.ohjumwhat.poll.PollRepository;

/**
 * 정기 투표 규칙에 맞춰 오늘의 투표를 연다. 1분마다 {@link PollScheduler}가 호출한다.
 *
 * 오늘 요일이 규칙에 포함되고 지금이 오픈~마감 사이인데 (규칙, 오늘) 투표가 없으면 만든다.
 * 제목은 규칙 이름이고, 이름의 날짜 토큰은 오늘 날짜로 바꾼다({@link ScheduleName}).
 * 같은 날 두 번 만들어지지 않도록 polls(schedule_id, poll_date) UNIQUE 제약에 기대고, 위반은 무시한다.
 * 그래서 서버가 오픈 시각에 꺼져 있었어도 마감 전에 다시 켜지면 그날 투표가 열린다.
 * 규칙마다 따로 저장해서, 한 규칙이 실패해도 다른 규칙의 투표는 열린다.
 */
@Slf4j
@Service
public class ScheduledPollOpener {

	private final PollScheduleRepository scheduleRepository;

	private final PollRepository pollRepository;

	private final Clock clock;

	public ScheduledPollOpener(PollScheduleRepository scheduleRepository, PollRepository pollRepository, Clock clock) {
		this.scheduleRepository = scheduleRepository;
		this.pollRepository = pollRepository;
		this.clock = clock;
	}

	/** @return 이번에 새로 연 투표 수 */
	public int openDuePolls() {
		ZonedDateTime now = Instant.now(clock).atZone(TimeConfig.KST);
		LocalDate today = now.toLocalDate();
		LocalTime time = now.toLocalTime();
		int opened = 0;
		for (PollSchedule schedule : scheduleRepository.findAll()) {
			boolean due = schedule.runsOn(today.getDayOfWeek()) && !time.isBefore(schedule.getOpenTime())
					&& time.isBefore(schedule.getCloseTime());
			if (!due || pollRepository.existsByScheduleIdAndPollDate(schedule.getId(), today)) {
				continue;
			}
			String title = ScheduleName.render(schedule.getName(), today);
			try {
				Poll poll = pollRepository.saveAndFlush(Poll.scheduled(schedule.getOrganizationId(), schedule.getId(), title,
						today, at(today, schedule.getOpenTime()), at(today, schedule.getCloseTime())));
				opened++;
				log.info("정기 투표 열림: pollId={}, scheduleId={}, organizationId={}", poll.getId(), schedule.getId(),
						schedule.getOrganizationId());
			}
			catch (DataIntegrityViolationException e) {
				// 다른 실행이 먼저 만들었다. (schedule_id, poll_date) UNIQUE
				log.debug("정기 투표가 이미 있음: scheduleId={}, date={}", schedule.getId(), today);
			}
		}
		return opened;
	}

	private static Instant at(LocalDate date, LocalTime time) {
		return ZonedDateTime.of(date, time, TimeConfig.KST).toInstant();
	}
}
