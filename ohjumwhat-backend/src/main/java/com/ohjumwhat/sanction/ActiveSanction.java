package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.List;

/**
 * 지금 걸려 있는 제재(GET /api/me의 sanctions). 화면이 이것으로 막힌 기능을 끄고 끝나는 시각을 보여준다(lib/sanctions.ts).
 *
 * @param restrictions 막은 기능(비어 있지 않다)
 * @param endsAt 끝나는 시각(null이면 관리자가 해제할 때까지)
 * @param note 관리자 설명(없으면 null)
 */
public record ActiveSanction(Long id, List<Restriction> restrictions, Instant endsAt, SanctionReason reason,
		String note, Instant createdAt) {

	static ActiveSanction of(UserSanction sanction) {
		return new ActiveSanction(sanction.getId(), sanction.getRestrictions(), sanction.getEndsAt(),
				sanction.getReason(), sanction.getNote(), sanction.getCreatedAt());
	}
}
