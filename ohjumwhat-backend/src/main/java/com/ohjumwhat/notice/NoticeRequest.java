package com.ohjumwhat.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 개발자 노트 쓰기·고치기. 앞뒤 공백은 저장할 때 지운다. 본문 형식은 일반 텍스트(빈 줄 = 문단, "- " = 목록). */
public record NoticeRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 100, message = "제목은 100자 이하로 입력해 주세요.")
		String title,
		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 5000, message = "내용은 5,000자 이하로 입력해 주세요.")
		String body) {
}
