package com.ohjumwhat.guestbook;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 방명록 한 쪽(최신순 10개). 지운 글은 빠지고, 제한된 글은 들어가며 개수에도 센다.
 * 쪽이 끝을 넘으면 entries는 비고 totalPages·totalCount는 실제 값이다(글이 없으면 totalPages도 0).
 *
 * @param owner 보는 사람이 방명록 주인인지(주인은 자기 방명록에 쓸 수 없다)
 * @param seenAt 주인일 때만, 내 방명록을 마지막으로 본 시각(없으면 null = 모두 새 글). 주인이 아니면 null
 */
@Schema(description = "방명록 한 쪽(최신순 10개). 지운 글은 빠지고, 제한된 글은 들어가며 개수에도 센다")
public record GuestbookPageResponse(@Schema(description = "글(최신순). 쪽이 끝을 넘으면 빈 배열") List<GuestbookEntryResponse> entries,
		@Schema(description = "이 쪽 번호(0부터)", example = "0") int page,
		@Schema(description = "전체 쪽 수(글이 없으면 0)", example = "3") int totalPages,
		@Schema(description = "전체 글 수(탭의 「방명록 N」)", example = "25") long totalCount,
		@Schema(description = "보는 사람이 방명록 주인인지(주인은 입력창이 없다)", example = "false") boolean owner,
		@Schema(description = "주인일 때만: 내 방명록을 마지막으로 본 시각(UTC). 이보다 뒤의 글이 NEW다. 한 번도 안 봤으면 null(모두 새 글), "
				+ "주인이 아니면 null", example = "2026-10-11T09:00:00Z", nullable = true)
		Instant seenAt) {
}
