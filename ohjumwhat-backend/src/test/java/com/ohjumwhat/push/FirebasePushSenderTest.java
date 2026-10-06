package com.ohjumwhat.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

class FirebasePushSenderTest {

	@Test
	void 구독이_해제됐거나_다른_프로젝트의_기기만_지울_기기로_본다() {
		List<String> fids = List.of("ok", "unregistered", "mismatch", "unavailable", "quota", "invalid", "unknown");
		List<MessagingErrorCode> errors = Arrays.asList(null, MessagingErrorCode.UNREGISTERED,
				MessagingErrorCode.SENDER_ID_MISMATCH, MessagingErrorCode.UNAVAILABLE, MessagingErrorCode.QUOTA_EXCEEDED,
				MessagingErrorCode.INVALID_ARGUMENT, null);

		assertThat(FirebasePushSender.staleFids(fids, errors)).containsExactly("unregistered", "mismatch");
	}

	@Test
	void 일시_장애는_지우지_않는다() {
		for (MessagingErrorCode code : MessagingErrorCode.values()) {
			boolean stale = code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH;
			assertThat(FirebasePushSender.isStale(code)).as(code.name()).isEqualTo(stale);
		}
		assertThat(FirebasePushSender.isStale(null)).isFalse();
	}

	@Test
	void 오류_코드가_없으면_UNKNOWN이다() {
		assertThat(FirebasePushSender.errorCode(null)).isEqualTo("UNKNOWN");
	}

	@Test
	void FID로_보낼_메시지를_만든다() {
		PushMessage message = PushMessages.letter(true, false, null).orElseThrow();

		assertThatCode(() -> FirebasePushSender.multicast(List.of("fid_phone_0000000001", "fid_laptop_000000002"),
				message)).doesNotThrowAnyException();
	}
}
