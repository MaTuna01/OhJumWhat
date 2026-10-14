package com.ohjumwhat.letter;

import java.time.Instant;

import com.ohjumwhat.poll.PersonResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 차단한 사람.
 * @param anonymous 익명 쪽지에서 차단했다(person은 null, 「익명 쪽지를 보낸 사람」)
 * @param person 실명으로 차단한 사람(탈퇴했으면 null)
 * @param preview 차단한 쪽지의 첫 줄(쪽지가 없으면 null)
 */
@Schema(description = "차단 하나. 실명 차단은 사람 단위, 익명 차단은 쪽지 한 통 단위다")
public record LetterBlockResponse(@Schema(description = "차단 ID(차단 풀기에 쓴다)", example = "5") Long id,
		@Schema(description = "익명 쪽지에서 차단했는지. true면 person은 null이고 「익명 쪽지를 보낸 사람」으로 보여준다", example = "false")
		boolean anonymous,
		@Schema(description = "실명으로 차단한 사람. 익명 차단이거나 탈퇴했으면 null", nullable = true) PersonResponse person,
		@Schema(description = "차단한 쪽지의 첫 줄(40자, 넘으면 …). 쪽지가 없으면 null", example = "오늘 점심 같이 가요!", nullable = true)
		String preview,
		@Schema(description = "차단한 시각(UTC)", example = "2026-10-12T02:30:00Z") Instant createdAt) {
}
