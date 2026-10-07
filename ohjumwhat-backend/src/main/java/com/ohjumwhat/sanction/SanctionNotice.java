package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.List;

/**
 * 본인에게 보여줄 제재 안내 한 건(GET /api/sanctions/alerts). 안내 창을 보기 전에 끝났거나 해제됐어도 그대로 알린다(status).
 *
 * @param resets 비운 프로필 항목
 * @param endsAt 제한이 끝나는 시각(제한이 없거나 해제할 때까지면 null)
 * @param liftedAt 해제한 시각(해제하지 않았으면 null)
 */
public record SanctionNotice(Long id, List<Restriction> restrictions, List<ProfileReset> resets,
		SanctionReason reason, String note, Instant createdAt, Instant endsAt, Instant liftedAt,
		SanctionStatus status) {

	static SanctionNotice of(UserSanction sanction, Instant now) {
		return new SanctionNotice(sanction.getId(), sanction.getRestrictions(), sanction.getResets(),
				sanction.getReason(), sanction.getNote(), sanction.getCreatedAt(), sanction.getEndsAt(),
				sanction.getLiftedAt(), sanction.status(now));
	}
}
