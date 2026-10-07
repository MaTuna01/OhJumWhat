package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.user.User;

/**
 * 관리자 콘솔의 제재 한 줄. 사람 이름은 별명(없으면 구글 이름), 사진은 올린 사진(없으면 구글 사진)이다.
 * 회원이 지워지면 제재도 함께 지워지므로 userId는 언제나 있다.
 *
 * @param createdByName 건 관리자(관리자 회원이 지워졌으면 null)
 * @param liftedByName 해제한 관리자(해제하지 않았거나 지워졌으면 null)
 * @param status 지금 시각 기준 상태
 */
public record SanctionRow(Long id, Long userId, String userName, String userEmail, String userProfileImageUrl,
		List<Restriction> restrictions, List<ProfileReset> resets, SanctionReason reason, String note,
		Instant createdAt, Instant endsAt, Instant liftedAt, String createdByName, String liftedByName,
		SanctionStatus status) {

	/**
	 * JPQL로 고른 재료(UserSanctionRepository). 상태는 지금 시각으로 서비스가 정한다.
	 *
	 * @param photoKey 올린 사진의 키(없으면 null)
	 * @param googleUrl 구글 사진 주소(없으면 null)
	 */
	public record Source(UserSanction sanction, String userName, String userEmail, String photoKey, String googleUrl,
			String createdByName, String liftedByName) {

		SanctionRow toRow(Instant now) {
			return new SanctionRow(sanction.getId(), sanction.getUserId(), userName, userEmail,
					User.photoUrl(photoKey, googleUrl), sanction.getRestrictions(), sanction.getResets(),
					sanction.getReason(), sanction.getNote(), sanction.getCreatedAt(), sanction.getEndsAt(),
					sanction.getLiftedAt(), createdByName, liftedByName, sanction.status(now));
		}
	}
}
