package com.ohjumwhat.chat;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채팅 한 페이지(오래된 → 최신).
 *
 * @param hasMore 이보다 오래된 메시지가 더 있는지(「이전 메시지 더 보기」)
 * @param lastReadId 내가 마지막으로 본 메시지 ID(없으면 0, 관리자 조회는 0)
 */
@Schema(description = "채팅 한 페이지(최대 50개)")
public record ChatMessagesResponse(
		@Schema(description = "메시지(오래된 → 최신)") List<ChatMessageResponse> messages,
		@Schema(description = "이보다 오래된 메시지가 더 있는지(「이전 메시지 더 보기」, 다음 요청의 before = 첫 메시지 ID)",
				example = "true") boolean hasMore,
		@Schema(description = "내가 마지막으로 본 메시지 ID(없으면 0, 관리자 조회는 0). 안 읽음 = 남이 쓴 지우지 않은 메시지 중 이보다 뒤",
				example = "1230") long lastReadId) {
}
