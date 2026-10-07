package com.ohjumwhat.sanction;

/** 제재 사유 분류. 이름은 API·DB(user_sanctions.reason)에 그대로 쓴다. 자세한 설명은 관리자 설명(note)에 적는다. */
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
