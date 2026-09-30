package com.ohjumwhat.organization;

public record OrganizationResponse(Long id, String name, String inviteToken, long memberCount) {
}
