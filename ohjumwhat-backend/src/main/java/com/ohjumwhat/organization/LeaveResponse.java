package com.ohjumwhat.organization;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param organizationDeleted 마지막 멤버가 탈퇴해 조직이 삭제됐는지
 */
@Schema(description = "조직 탈퇴 결과")
public record LeaveResponse(
		@Schema(description = "마지막 멤버라서 조직이 삭제됐는지", example = "false") boolean organizationDeleted) {
}
