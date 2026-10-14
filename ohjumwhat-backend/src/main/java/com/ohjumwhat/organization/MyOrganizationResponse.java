package com.ohjumwhat.organization;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param hasOpenPollToday 오늘 진행 중인 투표가 있는지
 */
@Schema(description = "내가 속한 조직(마이페이지 「내 조직」)")
public record MyOrganizationResponse(
		@Schema(description = "조직 ID", example = "1") Long id,
		@Schema(description = "조직 이름", example = "개발팀") String name,
		@Schema(description = "지금 인원", example = "10") long memberCount,
		@Schema(description = "오늘(한국 날짜) 진행 중인 투표가 있는지", example = "true") boolean hasOpenPollToday) {
}
