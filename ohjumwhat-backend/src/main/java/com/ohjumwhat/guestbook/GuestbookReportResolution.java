package com.ohjumwhat.guestbook;

/** 관리자가 방명록 신고를 처리한 결과 */
public enum GuestbookReportResolution {

	/** 글 제한: 본문을 누구에게도 내보내지 않고, 쓴 사람에게 경고한다. */
	RESTRICTED,

	/** 문제 없음: 글은 그대로 둔다. */
	DISMISSED
}
