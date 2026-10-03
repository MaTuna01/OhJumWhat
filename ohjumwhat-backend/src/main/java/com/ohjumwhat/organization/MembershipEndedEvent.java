package com.ohjumwhat.organization;

/** 멤버가 조직을 떠났다(탈퇴·관리자 내보내기·강제 탈퇴). 커밋 뒤에 그 사람의 그 조직 채팅 연결을 끊는다. */
public record MembershipEndedEvent(Long organizationId, Long userId) {
}
