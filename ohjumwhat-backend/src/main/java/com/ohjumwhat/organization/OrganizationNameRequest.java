package com.ohjumwhat.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 조직 만들기·이름 변경 요청 */
@Schema(description = "조직 만들기·이름 바꾸기")
public record OrganizationNameRequest(
		@Schema(description = "조직 이름(앞뒤 공백 제거, 50자)", example = "개발팀", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "조직 이름을 입력해 주세요.")
		@Size(max = 50, message = "조직 이름은 50자 이하로 입력해 주세요.")
		String name) {
}
