package com.ohjumwhat.sanction;

/**
 * 제재로 막는 기능. 이름은 API·DB(user_sanctions.restrictions)에 그대로 쓰고, 화면의 lib/sanctions.ts와 같다.
 * 투표 참여(고르기·패스·취소)와 지우기·읽음·신고·차단·조직 탈퇴는 어떤 제재로도 막지 않는다.
 */
public enum Restriction {

	/** 활동 정지: 아래 전부 + 조직 만들기·초대로 참여·조직 이름·조직 위치 바꾸기. 다른 제한과 함께 두지 않는다. */
	SUSPEND("활동이 정지된 상태예요(%s). 투표 참여만 할 수 있어요."),

	/** 투표 만들기·수정·지금 마감·삭제, 메뉴 추가, 식당 붙이기·고치기, 메뉴 댓글 쓰기·고치기, 정기 투표 만들기·수정·삭제 */
	POLL("관리자가 메뉴 올리기·댓글·투표 관리를 제한했어요(%s). 투표 참여는 할 수 있어요."),

	/** 채팅 보내기·고치기 */
	CHAT("관리자가 채팅을 제한했어요(%s)."),

	/** 쪽지 보내기·답장 */
	LETTER("관리자가 쪽지 보내기를 제한했어요(%s)."),

	/** 방명록 쓰기 */
	GUESTBOOK("관리자가 방명록 쓰기를 제한했어요(%s)."),

	/** 별명, 사진 올리기·되돌리기, 소개, 상세 프로필 */
	PROFILE("관리자가 프로필 수정을 제한했어요(%s).");

	/** 막혔을 때(423)의 문구. %s는 끝나는 시각(「10월 14일 오후 6:00까지」 또는 「해제될 때까지」) */
	private final String message;

	Restriction(String message) {
		this.message = message;
	}

	String message(String until) {
		return message.formatted(until);
	}
}
