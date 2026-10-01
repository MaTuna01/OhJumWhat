package com.ohjumwhat.notice;

/** 새 소식의 종류 */
public enum NoticeKind {

	/** 업데이트(릴리스 노트). 저장소의 release-notes 파일에서 자동으로 게시하고, 콘솔에서는 고칠 수 없다. */
	RELEASE,

	/** 개발자 노트. 관리자가 콘솔에서 쓰고 고치고 지운다. */
	NOTE
}
