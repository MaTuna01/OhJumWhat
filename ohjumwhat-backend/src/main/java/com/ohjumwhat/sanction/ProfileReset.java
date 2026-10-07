package com.ohjumwhat.sanction;

/** 제재로 비우는 프로필 항목. 이름은 API·DB(user_sanctions.resets)에 그대로 쓴다. 한 번 비우면 해제해도 되돌리지 않는다. */
public enum ProfileReset {

	/** 별명 → 구글 이름 */
	NICKNAME,

	/** 올린 사진 → 구글 사진(파일은 커밋 뒤에 지운다) */
	PHOTO,

	/** 한줄 소개와 좋아하는 음식 */
	INTRO,

	/** 상세 프로필 다섯 항목(MBTI·퍼스널컬러·취미·나이·직급) */
	DETAILS
}
