package com.ohjumwhat.sanction;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 콘솔의 제재 한 줄. 사람 이름은 별명(없으면 구글 이름), 사진은 올린 사진(없으면 구글 사진)이다.
 * 회원이 지워지면 제재도 함께 지워지므로 userId는 언제나 있다.
 *
 * @param createdByName 건 관리자(관리자 회원이 지워졌으면 null)
 * @param liftedByName 해제한 관리자(해제하지 않았거나 지워졌으면 null)
 * @param status 지금 시각 기준 상태
 */
@Schema(description = "관리자 콘솔의 제재 한 줄(A03b·A10). 회원이 지워지면 제재도 함께 지워지므로 userId는 언제나 있다")
public record SanctionRow(@Schema(description = "제재 ID", example = "3") Long id,
		@Schema(description = "제재받은 회원 ID", example = "7") Long userId,
		@Schema(description = "회원 이름(별명, 없으면 구글 이름)", example = "김오점") String userName,
		@Schema(description = "회원 이메일", example = "kim@example.com") String userEmail,
		@Schema(description = "올린 사진, 없으면 구글 사진. 둘 다 없으면 null", example = "/api/photos/3f2a9c1e5b7d4f60a1b2c3d4e5f60718.jpg", nullable = true)
		String userProfileImageUrl,
		@Schema(description = "막은 기능. 없으면 빈 배열", example = "[\"CHAT\"]") List<Restriction> restrictions,
		@Schema(description = "비운 프로필 항목. 없으면 빈 배열", example = "[]") List<ProfileReset> resets,
		SanctionReason reason,
		@Schema(description = "관리자 설명. 없으면 null", example = "채팅에서 반복적으로 욕설을 했어요.", nullable = true) String note,
		@Schema(description = "건 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt,
		@Schema(description = "끝나는 시각(UTC). 제한이 없거나 해제할 때까지면 null", example = "2026-10-19T02:30:00Z", nullable = true)
		Instant endsAt,
		@Schema(description = "해제한 시각(UTC). 해제하지 않았으면 null", nullable = true) Instant liftedAt,
		@Schema(description = "건 관리자 이름(관리자 회원이 지워졌으면 null)", example = "관리자", nullable = true) String createdByName,
		@Schema(description = "해제한 관리자 이름(해제하지 않았거나 지워졌으면 null)", nullable = true) String liftedByName,
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
