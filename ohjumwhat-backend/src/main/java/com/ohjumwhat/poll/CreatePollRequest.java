package com.ohjumwhat.poll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param closesAt 오늘(한국 시간) 마감 시각, "HH:mm"
 */
public record CreatePollRequest(
		@NotBlank(message = "투표 제목을 입력해 주세요.")
		@Size(max = 100, message = "투표 제목은 100자 이하로 입력해 주세요.")
		String title,
		@NotBlank(message = "마감 시간을 입력해 주세요.")
		@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "마감 시간 형식이 올바르지 않아요.")
		String closesAt) {
}
