package com.ohjumwhat.letter;

import java.util.Locale;

import com.ohjumwhat.common.ApiException;

import io.swagger.v3.oas.annotations.media.Schema;

/** 쪽지함: 받은 쪽지, 보낸 쪽지 */
@Schema(description = "쪽지함. RECEIVED = 받은 쪽지, SENT = 보낸 쪽지", example = "RECEIVED")
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
