package com.ohjumwhat.poll;

import com.ohjumwhat.user.User;

public record PersonResponse(Long userId, String name, String profileImageUrl) {

	static PersonResponse of(User user) {
		return new PersonResponse(user.getId(), user.getName(), user.getProfileImageUrl());
	}
}
