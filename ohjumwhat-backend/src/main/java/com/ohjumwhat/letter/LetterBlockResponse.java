package com.ohjumwhat.letter;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

/**
 * 차단한 사람.
 * @param anonymous 익명 쪽지에서 차단했다(person은 null, 「익명 쪽지를 보낸 사람」)
 * @param person 실명으로 차단한 사람(탈퇴했으면 null)
 * @param preview 차단한 쪽지의 첫 줄(쪽지가 없으면 null)
 */
public record LetterBlockResponse(Long id, boolean anonymous, PersonResponse person, String preview,
		Instant createdAt) {
}
