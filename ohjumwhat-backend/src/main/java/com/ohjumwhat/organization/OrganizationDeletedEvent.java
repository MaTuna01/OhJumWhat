package com.ohjumwhat.organization;

/** 조직을 지웠다(마지막 멤버 탈퇴·관리자). 커밋 뒤에 그 조직의 채팅 연결을 모두 끊는다. */
public record OrganizationDeletedEvent(Long organizationId) {
}
