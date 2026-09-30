package com.ohjumwhat.organization;

import java.time.Instant;

public record MemberResponse(Long userId, String name, String profileImageUrl, Instant joinedAt) {
}
