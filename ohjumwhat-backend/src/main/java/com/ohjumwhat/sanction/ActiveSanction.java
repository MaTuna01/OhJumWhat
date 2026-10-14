package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지금 걸려 있는 제재(GET /api/me의 sanctions). 화면이 이것으로 막힌 기능을 끄고 끝나는 시각을 보여준다(lib/sanctions.ts).
 *
 * @param restrictions 막은 기능(비어 있지 않다)
 * @param endsAt 끝나는 시각(null이면 관리자가 해제할 때까지)
 * @param note 관리자 설명(없으면 null)
 */
@Schema(description = "지금 걸려 있는 제재. 기능 X가 막힘 = X 또는 SUSPEND가 든 제재가 하나라도 있음, 끝나는 시각 = 그중 가장 늦은 것"
		+ "(하나라도 무기한이면 무기한). 화면은 endsAt이 지나면 스스로 푼다")
public record ActiveSanction(@Schema(description = "제재 ID", example = "3") Long id,
		@Schema(description = "막은 기능(비어 있지 않다)", example = "[\"CHAT\"]") List<Restriction> restrictions,
		@Schema(description = "끝나는 시각(UTC). null이면 관리자가 해제할 때까지", example = "2026-10-14T09:00:00Z", nullable = true)
		Instant endsAt,
		SanctionReason reason,
		@Schema(description = "관리자 설명(본인에게 보인다). 없으면 null", example = "채팅에서 반복적으로 욕설을 했어요.", nullable = true)
		String note,
		@Schema(description = "제재를 건 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt) {

	static ActiveSanction of(UserSanction sanction) {
		return new ActiveSanction(sanction.getId(), sanction.getRestrictions(), sanction.getEndsAt(),
				sanction.getReason(), sanction.getNote(), sanction.getCreatedAt());
	}
}
