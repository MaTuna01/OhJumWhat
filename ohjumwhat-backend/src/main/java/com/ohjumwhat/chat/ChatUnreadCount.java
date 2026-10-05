package com.ohjumwhat.chat;

/**
 * 투표별 안 읽은 채팅 메시지 수({@link ChatReadRepository#countUnread}). 안 읽은 메시지가 없는 투표는 빠진다.
 *
 * @param count 남이 쓴(탈퇴한 사용자 포함), 지우지 않은, 읽은 위치보다 뒤의 메시지 수
 */
public record ChatUnreadCount(Long pollId, long count) {
}
