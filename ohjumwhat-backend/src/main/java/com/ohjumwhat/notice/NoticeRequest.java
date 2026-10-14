package com.ohjumwhat.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 개발자 노트 쓰기·고치기. 앞뒤 공백은 저장할 때 지운다. 본문 형식은 일반 텍스트(빈 줄 = 문단, "- " = 목록). */
@Schema(description = "개발자 노트 쓰기·고치기. 앞뒤 공백은 저장할 때 지운다")
public record NoticeRequest(
		@Schema(description = "제목(100자)", example = "점심 투표가 더 빨라졌어요", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 100, message = "제목은 100자 이하로 입력해 주세요.")
		String title,
		@Schema(description = "본문(5,000자, 일반 텍스트: 빈 줄 = 문단, \"- \" = 목록, http(s) 주소 = 링크)",
				example = "투표 상세가 더 빨리 열려요.\n\n- 메뉴가 많아도 끊기지 않아요", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 5000, message = "내용은 5,000자 이하로 입력해 주세요.")
		String body) {
}
