package com.ohjumwhat.letter;

/**
 * 받는 사람의 쪽지함에 쪽지(답장 포함)가 들어갔다(받는 사람에게 알릴 거리). 차단으로 받지 않은 쪽지는 내지 않는다.
 * 보낸 사람·익명 여부·본문은 싣지 않는다(알릴 때 받은 쪽지함과 같은 쿼리로 다시 읽는다).
 */
public record LetterDeliveredEvent(Long letterId, Long recipientId) {
}
