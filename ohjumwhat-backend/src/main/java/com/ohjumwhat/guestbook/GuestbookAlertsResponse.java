package com.ohjumwhat.guestbook;

import java.time.Instant;
import java.util.List;

import com.ohjumwhat.poll.PersonResponse;
import com.ohjumwhat.user.User;

/**
 * 방명록 알림 요약.
 *
 * @param newEntryCount 내 방명록에서 지우지 않은 글 중 마지막으로 본 뒤에 쓰인 글 수
 * @param warnings 관리자가 제한한 내 글 중 마지막으로 확인한 뒤에 제한된 것(제한한 순서)
 */
public record GuestbookAlertsResponse(long newEntryCount, List<Warning> warnings) {

	/**
	 * 제한된 내 글. body는 쓴 사람 본인에게만 보여주는 원문이다(지운 글도 포함).
	 *
	 * @param owner 그 글이 있는 방명록의 주인(강제 탈퇴했으면 null)
	 */
	public record Warning(Long entryId, PersonResponse owner, String body, Instant writtenAt, Instant restrictedAt) {

		/** JPQL용: 주인의 이름(별명, 없으면 구글 이름)과 올린 사진의 키·구글 사진 주소로 주인을 만든다. */
		Warning(Long entryId, Long ownerId, String ownerName, String photoKey, String googleUrl, String body,
				Instant writtenAt, Instant restrictedAt) {
			this(entryId,
					ownerId == null ? null : new PersonResponse(ownerId, ownerName, User.photoUrl(photoKey, googleUrl)),
					body, writtenAt, restrictedAt);
		}
	}
}
