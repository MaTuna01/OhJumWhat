package com.ohjumwhat.schedule;

import java.time.format.DateTimeFormatter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param openTime 한국 시간 "HH:mm"
 * @param closeTime 한국 시간 "HH:mm"
 */
@Schema(description = "정기 투표 규칙")
public record ScheduleResponse(
		@Schema(description = "규칙 ID", example = "3") Long id,
		@Schema(description = "규칙 이름(`${오늘날짜}` 토큰은 그대로 온다. 화면은 「오늘 날짜」 칩으로 보여준다)", example = "${오늘날짜} 점심")
		String name,
		@Schema(description = "요일 비트마스크: 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64(평일 = 31)", example = "31") int daysOfWeek,
		@Schema(description = "투표를 여는 시각(한국, HH:mm)", example = "11:00") String openTime,
		@Schema(description = "마감 시각(한국, HH:mm)", example = "11:50") String closeTime) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	public static ScheduleResponse of(PollSchedule schedule) {
		return new ScheduleResponse(schedule.getId(), schedule.getName(), schedule.getDaysOfWeek(),
				schedule.getOpenTime().format(HH_MM), schedule.getCloseTime().format(HH_MM));
	}
}
