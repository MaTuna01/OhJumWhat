package com.ohjumwhat.guestbook;

/** 관리자가 신고된 방명록 글을 제한했다(쓴 사람에게 경고할 거리). 쓴 사람이 있을 때만 낸다. 본문은 싣지 않는다. */
public record GuestbookEntryRestrictedEvent(Long entryId, Long authorId, Long ownerId) {
}
