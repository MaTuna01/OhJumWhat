package com.ohjumwhat.poll;

/** 투표를 지웠다(사용자·관리자). 커밋 뒤에 그 투표의 채팅 연결을 끊는다. */
public record PollDeletedEvent(Long pollId) {
}
