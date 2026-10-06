package com.ohjumwhat.guestbook;

/** 방명록에 새 글이 올라왔다(주인에게 알릴 거리). 본문은 싣지 않는다. */
public record GuestbookEntryCreatedEvent(Long entryId, Long ownerId, Long authorId) {
}
