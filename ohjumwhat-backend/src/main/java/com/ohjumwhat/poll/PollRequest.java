package com.ohjumwhat.poll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 투표 만들기·수정 요청.
 *
 * @param closesAt 투표 날짜(한국 시간)의 마감 시각, "HH:mm"
 */
public record PollRequest(
		@Schema(description = "투표 제목(100자)", example = "오늘 점심", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "투표 제목을 입력해 주세요.")
		@Size(max = 100, message = "투표 제목은 100자 이하로 입력해 주세요.")
		String title,
		@Schema(description = "마감 시간, 투표 날짜(한국)의 \"HH:mm\"", example = "11:50", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "마감 시간을 입력해 주세요.")
		@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "마감 시간 형식이 올바르지 않아요.")
		String closesAt) {
}
