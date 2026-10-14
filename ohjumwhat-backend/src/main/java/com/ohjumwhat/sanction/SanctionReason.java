package com.ohjumwhat.sanction;

import io.swagger.v3.oas.annotations.media.Schema;

/** 제재 사유 분류. 이름은 API·DB(user_sanctions.reason)에 그대로 쓴다. 자세한 설명은 관리자 설명(note)에 적는다. */
@Schema(description = "제재·사람 신고의 사유 분류. PROFILE = 부적절한 프로필, ABUSE = 욕설·비방, SPAM = 도배, ETC = 기타",
		example = "ABUSE")
public enum SanctionReason {

	/** 부적절한 프로필 */
	PROFILE,

	/** 욕설·비방 */
	ABUSE,

	/** 도배 */
	SPAM,

	/** 기타 */
	ETC
}
