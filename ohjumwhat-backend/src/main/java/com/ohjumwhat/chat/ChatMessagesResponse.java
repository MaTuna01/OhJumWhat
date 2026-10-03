package com.ohjumwhat.chat;

import java.util.List;

/**
 * 채팅 한 페이지(오래된 → 최신).
 *
 * @param hasMore 이보다 오래된 메시지가 더 있는지(「이전 메시지 더 보기」)
 */
public record ChatMessagesResponse(List<ChatMessageResponse> messages, boolean hasMore) {
}
