package com.ohjumwhat.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정기 투표 규칙 추가·수정 요청.
 *
 * @param daysOfWeek 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64를 더한 값 (평일 = 31)
 * @param openTime 한국 시간 "HH:mm"
 * @param closeTime 한국 시간 "HH:mm", openTime보다 늦어야 한다
 */
@Schema(description = "정기 투표 규칙 추가·수정(통째로 바꾼다)")
public record ScheduleRequest(
		@Schema(description = "규칙 이름 = 열리는 투표의 제목(앞뒤 공백 제거, 50자). `${오늘날짜}`를 넣으면 투표를 열 때 그날의 한국 날짜 "
				+ "YYYY-MM-DD로 바뀐다(토큰은 이것 하나, 길이는 10자로 센다). 이름은 토큰 그대로 저장한다",
				example = "${오늘날짜} 점심", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "규칙 이름을 입력해 주세요.")
		@Size(max = 50, message = "규칙 이름은 50자 이하로 입력해 주세요.")
		String name,
		@Schema(description = "요일 비트마스크: 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64를 더한 값(1~127, 평일 = 31)",
				example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "요일을 하나 이상 골라 주세요.")
		@Min(value = 1, message = "요일을 하나 이상 골라 주세요.")
		@Max(value = 127, message = "요일 값이 올바르지 않아요.")
		Integer daysOfWeek,
		@Schema(description = "투표를 여는 시각(한국, HH:mm)", example = "11:00", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "오픈 시간을 입력해 주세요.")
		@Pattern(regexp = TIME, message = "오픈 시간 형식이 올바르지 않아요.")
		String openTime,
		@Schema(description = "마감 시각(한국, HH:mm). openTime보다 늦어야 한다(같은 날 안)", example = "11:50",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "마감 시간을 입력해 주세요.")
		@Pattern(regexp = TIME, message = "마감 시간 형식이 올바르지 않아요.")
		String closeTime) {

	static final String TIME = "^([01]\\d|2[0-3]):[0-5]\\d$";
}
