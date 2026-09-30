package com.ohjumwhat.organization;

/**
 * @param organizationDeleted 마지막 멤버가 탈퇴해 조직이 삭제됐는지
 */
public record LeaveResponse(boolean organizationDeleted) {
}
