package com.ohjumwhat.sanction;

/** 제재 상태. 저장하지 않고 지금 시각으로 서버가 계산해 응답에 넣는다(UserSanction.status). */
public enum SanctionStatus {

	/** 제한 있음, 해제되지 않았고 끝나지 않았다 */
	ACTIVE,

	/** 제한 있음, 끝나는 시각이 지났다 */
	EXPIRED,

	/** 제한 있음, 관리자가 해제했다 */
	LIFTED,

	/** 제한·초기화 둘 다 없음(경고만) */
	WARNING,

	/** 제한 없이 프로필 초기화만 */
	RESET_ONLY
}
