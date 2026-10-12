package com.ohjumwhat.poll;

import com.ohjumwhat.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** 명단에 보여줄 사람. name은 별명(없으면 구글 이름), profileImageUrl은 올린 사진(없으면 구글 사진) */
@Schema(description = "명단에 보여줄 사람")
public record PersonResponse(
		@Schema(description = "회원 ID", example = "7") Long userId,
		@Schema(description = "별명, 없으면 구글 이름", example = "김오점") String name,
		@Schema(description = "올린 사진(/api/photos/{key}.jpg), 없으면 구글 사진. 둘 다 없으면 null",
				example = "/api/photos/3f2a9c1e.jpg", nullable = true) String profileImageUrl) {

	static PersonResponse of(User user) {
		return new PersonResponse(user.getId(), user.getDisplayName(), user.getPhotoUrl());
	}
}
