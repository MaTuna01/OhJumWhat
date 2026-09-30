package com.ohjumwhat.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 조직 만들기·이름 변경 요청 */
public record OrganizationNameRequest(
		@NotBlank(message = "조직 이름을 입력해 주세요.")
		@Size(max = 50, message = "조직 이름은 50자 이하로 입력해 주세요.")
		String name) {
}
