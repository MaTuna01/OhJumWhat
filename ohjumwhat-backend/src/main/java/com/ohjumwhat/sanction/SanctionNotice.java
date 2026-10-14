package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 본인에게 보여줄 제재 안내 한 건(GET /api/sanctions/alerts). 안내 창을 보기 전에 끝났거나 해제됐어도 그대로 알린다(status).
 *
 * @param resets 비운 프로필 항목
 * @param endsAt 제한이 끝나는 시각(제한이 없거나 해제할 때까지면 null)
 * @param liftedAt 해제한 시각(해제하지 않았으면 null)
 */
@Schema(description = "제재 안내 한 건(00-S). 안내 창을 보기 전에 끝났거나 해제됐어도 그대로 알린다(status)")
public record SanctionNotice(@Schema(description = "제재 ID(「제재 안내 봤음」에 보낸다)", example = "3") Long id,
		@Schema(description = "막은 기능. 경고·초기화만이면 빈 배열", example = "[\"CHAT\"]") List<Restriction> restrictions,
		@Schema(description = "비운 프로필 항목. 없으면 빈 배열", example = "[]") List<ProfileReset> resets,
		SanctionReason reason,
		@Schema(description = "관리자 설명. 없으면 null", example = "채팅에서 반복적으로 욕설을 했어요.", nullable = true) String note,
		@Schema(description = "제재를 건 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt,
		@Schema(description = "제한이 끝나는 시각(UTC). 제한이 없거나 해제할 때까지면 null", example = "2026-10-14T09:00:00Z",
				nullable = true)
		Instant endsAt,
		@Schema(description = "관리자가 해제한 시각(UTC). 해제하지 않았으면 null", nullable = true) Instant liftedAt,
		SanctionStatus status) {

	static SanctionNotice of(UserSanction sanction, Instant now) {
		return new SanctionNotice(sanction.getId(), sanction.getRestrictions(), sanction.getResets(),
				sanction.getReason(), sanction.getNote(), sanction.getCreatedAt(), sanction.getEndsAt(),
				sanction.getLiftedAt(), sanction.status(now));
	}
}
