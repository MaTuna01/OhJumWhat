package com.ohjumwhat.guestbook;

import java.time.Instant;
import java.util.List;

/**
 * 방명록 한 쪽(최신순 10개). 지운 글은 빠지고, 제한된 글은 들어가며 개수에도 센다.
 * 쪽이 끝을 넘으면 entries는 비고 totalPages·totalCount는 실제 값이다(글이 없으면 totalPages도 0).
 *
 * @param owner 보는 사람이 방명록 주인인지(주인은 자기 방명록에 쓸 수 없다)
 * @param seenAt 주인일 때만, 내 방명록을 마지막으로 본 시각(없으면 null = 모두 새 글). 주인이 아니면 null
 */
public record GuestbookPageResponse(List<GuestbookEntryResponse> entries, int page, int totalPages, long totalCount,
		boolean owner, Instant seenAt) {
}
