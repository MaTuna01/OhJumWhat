package com.ohjumwhat.schedule;

import java.time.format.DateTimeFormatter;

/**
 * @param openTime 한국 시간 "HH:mm"
 * @param closeTime 한국 시간 "HH:mm"
 */
public record ScheduleResponse(Long id, String name, int daysOfWeek, String openTime, String closeTime) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	static ScheduleResponse of(PollSchedule schedule) {
		return new ScheduleResponse(schedule.getId(), schedule.getName(), schedule.getDaysOfWeek(),
				schedule.getOpenTime().format(HH_MM), schedule.getCloseTime().format(HH_MM));
	}
}
