package com.ohjumwhat.guestbook;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

/**
 * 방명록 글 한 줄(보는 사람 기준).
 *
 * @param author 쓴 사람(강제 탈퇴했으면 null, 화면은 「탈퇴한 사용자」)
 * @param body 본문(제한된 글이면 null, 누구에게도 보내지 않는다)
 * @param mine 내가 쓴 글
 * @param canDelete 지울 수 있는지(쓴 사람 또는 주인)
 * @param canReport 신고할 수 있는지(주인이고, 아직 신고하지 않았고, 제한되지 않은 글)
 * @param reported 주인이 이미 신고했는지(주인에게만 계산하고, 주인이 아니면 언제나 false)
 */
public record GuestbookEntryResponse(Long id, PersonResponse author, String body, Instant createdAt,
		boolean restricted, boolean mine, boolean canDelete, boolean canReport, boolean reported) {
}
