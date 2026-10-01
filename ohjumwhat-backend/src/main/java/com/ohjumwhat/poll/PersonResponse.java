package com.ohjumwhat.poll;

import com.ohjumwhat.user.User;

/** 명단에 보여줄 사람. name은 별명, 없으면 구글 이름 */
public record PersonResponse(Long userId, String name, String profileImageUrl) {

	static PersonResponse of(User user) {
		return new PersonResponse(user.getId(), user.getDisplayName(), user.getProfileImageUrl());
	}
}
