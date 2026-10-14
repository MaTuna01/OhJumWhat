package com.ohjumwhat.organization;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 수락 화면(D02)의 조직 정보")
public record InviteResponse(
		@Schema(description = "조직 ID", example = "1") Long organizationId,
		@Schema(description = "조직 이름", example = "개발팀") String name,
		@Schema(description = "지금 인원", example = "10") long memberCount,
		@Schema(description = "내가 이미 멤버인지(화면은 바로 조직 홈으로 보낸다)", example = "false") boolean alreadyMember) {
}
