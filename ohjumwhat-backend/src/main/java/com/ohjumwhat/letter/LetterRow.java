package com.ohjumwhat.letter;

import java.time.Instant;

import com.ohjumwhat.user.User;

/**
 * 쪽지 목록 행(보는 사람 기준). counterpart*는 상대(받은 쪽지는 보낸 사람, 보낸 쪽지는 받는 사람)이고, 숨겨야 하면(익명)
 * 쿼리가 아예 고르지 않아 null이다. senderPresent는 보낸 사람의 계정이 남아 있는지(답장할 수 있는지)만 알린다.
 */
public record LetterRow(Long id, Long organizationId, String organizationName, Long counterpartId,
		String counterpartName, String counterpartPhotoUrl, boolean anonymous, boolean recipientHidden,
		boolean senderPresent, String body, Instant createdAt, Instant readAt, Long replyToId) {

	/** JPQL용: 올린 사진의 키와 구글 사진 주소로 화면 사진(올린 사진, 없으면 구글 사진)을 정한다. */
	public LetterRow(Long id, Long organizationId, String organizationName, Long counterpartId, String counterpartName,
			String photoKey, String googleUrl, boolean anonymous, boolean recipientHidden, boolean senderPresent,
			String body, Instant createdAt, Instant readAt, Long replyToId) {
		this(id, organizationId, organizationName, counterpartId, counterpartName,
				counterpartId == null ? null : User.photoUrl(photoKey, googleUrl), anonymous, recipientHidden,
				senderPresent, body, createdAt, readAt, replyToId);
	}
}
