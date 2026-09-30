package com.ohjumwhat.organization;

public record InviteResponse(Long organizationId, String name, long memberCount, boolean alreadyMember) {
}
