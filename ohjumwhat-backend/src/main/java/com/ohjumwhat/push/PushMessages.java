package com.ohjumwhat.push;

import java.util.Optional;

/**
 * 푸시 문구 규칙. 이름은 받는 사람이 화면에서 보는 이름(별명, 없으면 구글 이름)이고, 20자(코드 포인트)를 넘으면 20자 + 「…」로
 * 자른다. 본문은 넣지 않는다.
 */
public final class PushMessages {

	static final int NAME_MAX_LENGTH = 20;

	private PushMessages() {
	}

	/** 내 방명록에 새 글: 「{쓴 사람}님이 방명록을 남겼어요」 */
	public static PushMessage guestbook(String authorName) {
		return new PushMessage(PushKind.GUESTBOOK, name(authorName) + "님이 방명록을 남겼어요");
	}

	/** 내가 남긴 방명록 글이 제한됐다 */
	public static PushMessage guestbookRestricted() {
		return new PushMessage(PushKind.GUESTBOOK_RESTRICTED, "남긴 방명록 글이 관리자에 의해 제한됐어요");
	}

	/**
	 * 새 쪽지·답장. 익명이면(쪽지·답장 모두) 이름 없이 「익명 쪽지가 왔어요」다.
	 *
	 * @param senderName 받은 쪽지함에 보이는 보낸 사람 이름(익명이면 쿼리가 고르지 않아 null)
	 * @return 실명인데 보낸 사람이 없으면(그사이 탈퇴) 비어 있다(보내지 않는다)
	 */
	public static Optional<PushMessage> letter(boolean anonymous, boolean reply, String senderName) {
		if (anonymous) {
			return Optional.of(new PushMessage(PushKind.LETTER, "익명 쪽지가 왔어요"));
		}
		if (senderName == null) {
			return Optional.empty();
		}
		return Optional.of(new PushMessage(PushKind.LETTER,
				name(senderName) + (reply ? "님이 답장을 보냈어요" : "님이 쪽지를 보냈어요")));
	}

	/**
	 * 관리자 제재. 사유·설명은 넣지 않는다.
	 *
	 * @param hasRestrictions 기능을 제한했다(초기화가 함께 있어도 이 문구)
	 * @param hasResets 프로필을 초기화했다
	 */
	public static PushMessage sanction(boolean hasRestrictions, boolean hasResets) {
		String title = hasRestrictions ? "관리자가 이용을 제한했어요"
				: hasResets ? "관리자가 프로필을 초기화했어요" : "관리자의 경고가 도착했어요";
		return new PushMessage(PushKind.SANCTION, title);
	}

	/** 20자(코드 포인트)를 넘으면 20자 + 「…」 */
	static String name(String name) {
		if (name.codePointCount(0, name.length()) <= NAME_MAX_LENGTH) {
			return name;
		}
		return name.substring(0, name.offsetByCodePoints(0, NAME_MAX_LENGTH)) + "…";
	}
}
