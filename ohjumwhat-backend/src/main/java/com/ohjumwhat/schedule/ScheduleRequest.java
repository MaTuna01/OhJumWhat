package com.ohjumwhat.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 정기 투표 규칙 추가·수정 요청.
 *
 * @param daysOfWeek 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64를 더한 값 (평일 = 31)
 * @param openTime 한국 시간 "HH:mm"
 * @param closeTime 한국 시간 "HH:mm", openTime보다 늦어야 한다
 */
public record ScheduleRequest(
		@NotBlank(message = "규칙 이름을 입력해 주세요.")
		@Size(max = 50, message = "규칙 이름은 50자 이하로 입력해 주세요.")
		String name,
		@NotNull(message = "요일을 하나 이상 골라 주세요.")
		@Min(value = 1, message = "요일을 하나 이상 골라 주세요.")
		@Max(value = 127, message = "요일 값이 올바르지 않아요.")
		Integer daysOfWeek,
		@NotBlank(message = "오픈 시간을 입력해 주세요.")
		@Pattern(regexp = TIME, message = "오픈 시간 형식이 올바르지 않아요.")
		String openTime,
		@NotBlank(message = "마감 시간을 입력해 주세요.")
		@Pattern(regexp = TIME, message = "마감 시간 형식이 올바르지 않아요.")
		String closeTime) {

	static final String TIME = "^([01]\\d|2[0-3]):[0-5]\\d$";
}
