package com.ohjumwhat.schedule;

import java.time.LocalTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.organization.MembershipService;

/** 정기 투표 규칙 관리. 조직의 모든 멤버가 같은 권한으로 추가·수정·삭제할 수 있다. */
@Slf4j
@Service
public class ScheduleService {

	private final PollScheduleRepository scheduleRepository;

	private final MembershipService membershipService;

	public ScheduleService(PollScheduleRepository scheduleRepository, MembershipService membershipService) {
		this.scheduleRepository = scheduleRepository;
		this.membershipService = membershipService;
	}

	@Transactional(readOnly = true)
	public List<ScheduleResponse> list(Long organizationId, Long userId) {
		membershipService.requireMember(organizationId, userId);
		return scheduleRepository.findByOrganizationIdOrderByOpenTimeAscIdAsc(organizationId).stream()
			.map(ScheduleResponse::of)
			.toList();
	}

	@Transactional
	public ScheduleResponse create(Long organizationId, Long userId, ScheduleRequest request) {
		membershipService.requireMember(organizationId, userId);
		String name = validName(request);
		LocalTime[] times = times(request);
		PollSchedule schedule = scheduleRepository.save(new PollSchedule(organizationId, name, request.daysOfWeek(),
				times[0], times[1]));
		log.info("정기 투표 규칙 추가: scheduleId={}, organizationId={}, userId={}", schedule.getId(), organizationId,
				userId);
		return ScheduleResponse.of(schedule);
	}

	/** 수정은 앞으로 열릴 투표부터 적용된다. 이미 열린 투표는 그대로 둔다. */
	@Transactional
	public ScheduleResponse update(Long organizationId, Long scheduleId, Long userId, ScheduleRequest request) {
		PollSchedule schedule = find(organizationId, scheduleId, userId);
		String name = validName(request);
		LocalTime[] times = times(request);
		schedule.update(name, request.daysOfWeek(), times[0], times[1]);
		log.info("정기 투표 규칙 수정: scheduleId={}, userId={}", scheduleId, userId);
		return ScheduleResponse.of(schedule);
	}

	/** 규칙을 지워도 이미 열린 투표는 남는다(polls.schedule_id만 NULL, DB ON DELETE SET NULL). */
	@Transactional
	public void delete(Long organizationId, Long scheduleId, Long userId) {
		scheduleRepository.delete(find(organizationId, scheduleId, userId));
		log.info("정기 투표 규칙 삭제: scheduleId={}, userId={}", scheduleId, userId);
	}

	private PollSchedule find(Long organizationId, Long scheduleId, Long userId) {
		membershipService.requireMember(organizationId, userId);
		return scheduleRepository.findById(scheduleId)
			.filter(s -> s.getOrganizationId().equals(organizationId))
			.orElseThrow(() -> ApiException.notFound("정기 투표 규칙을 찾을 수 없어요."));
	}

	/** 요청의 @Size는 글자 그대로 세므로, 날짜 토큰을 바뀐 길이로 센 한도는 여기서 본다. */
	private static String validName(ScheduleRequest request) {
		String name = request.name().strip();
		if (ScheduleName.length(name) > ScheduleName.MAX_LENGTH) {
			throw ApiException.badRequest(ScheduleName.TOO_LONG);
		}
		return name;
	}

	private static LocalTime[] times(ScheduleRequest request) {
		LocalTime open = LocalTime.parse(request.openTime());
		LocalTime close = LocalTime.parse(request.closeTime());
		if (!close.isAfter(open)) {
			throw ApiException.badRequest("마감 시간은 오픈 시간보다 늦어야 해요.");
		}
		return new LocalTime[] { open, close };
	}
}
