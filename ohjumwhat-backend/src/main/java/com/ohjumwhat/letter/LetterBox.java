package com.ohjumwhat.letter;

import java.util.Locale;

import com.ohjumwhat.common.ApiException;

/** 쪽지함: 받은 쪽지, 보낸 쪽지 */
public enum LetterBox {
	RECEIVED, SENT;

	/** 요청 값 received·sent(대소문자 무시). 비면 받은 쪽지 */
	static LetterBox parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return RECEIVED;
		}
		try {
			return valueOf(raw.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("쪽지함은 received나 sent예요.");
		}
	}
}
