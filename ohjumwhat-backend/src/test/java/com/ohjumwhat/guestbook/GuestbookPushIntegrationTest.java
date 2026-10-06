package com.ohjumwhat.guestbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.FakePushSenderConfiguration.Sent;
import com.ohjumwhat.user.User;

/** 방명록 푸시: 새 글은 주인에게, 제한은 쓴 사람에게 */
class GuestbookPushIntegrationTest extends GuestbookTestBase {

	static final String KIM_PHONE = "fid_kim_phone_000001";

	static final String LEE_PHONE = "fid_lee_phone_000002";

	@Test
	void 새_글은_주인에게_쓴_사람_이름으로_알린다() throws Exception {
		turnOn(kim, KIM_PHONE);
		turnOn(lee, LEE_PHONE);

		writeOk(lee, kim, "점심 맛있게 드세요");
		pushDispatcher.drain();

		assertThat(pushSender.sent()).hasSize(1);
		Sent sent = pushSender.sent().get(0);
		assertThat(sent.fids()).containsExactly(KIM_PHONE);
		assertThat(sent.message().data()).isEqualTo(Map.of("kind", "GUESTBOOK", "title", "이영희님이 방명록을 남겼어요", "url",
				"/me#guestbook", "tag", "ohjumwhat-guestbook"));
	}

	@Test
	void 별명이_있으면_별명으로_알린다() throws Exception {
		jdbcTemplate.update("update users set nickname = '영희별명' where id = ?", lee.getId());
		turnOn(kim, KIM_PHONE);

		writeOk(lee, kim, "안녕하세요");
		pushDispatcher.drain();

		assertThat(pushSender.sent()).extracting(sent -> sent.message().title())
			.containsExactly("영희별명님이 방명록을 남겼어요");
	}

	@Test
	void 알리기_전에_지운_글은_알리지_않는다() throws Exception {
		turnOn(kim, KIM_PHONE);
		long entry = writeOk(lee, kim, "잘못 썼어요");
		deleteEntry(lee, entry).andExpect(status().isNoContent());

		pushDispatcher.drain();

		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void 글이_제한되면_쓴_사람에게_알린다() throws Exception {
		turnOn(lee, LEE_PHONE);
		User admin = admin();
		long entry = writeOk(lee, kim, "나쁜 말");
		report(kim, entry, "{\"reason\": \"욕설\"}").andExpect(status().isNoContent());
		pushDispatcher.drain();
		assertThat(pushSender.sent()).isEmpty(); // 주인(김)은 알림을 켜지 않았다.

		restrict(admin, reportIdOf(admin, entry)).andExpect(status().isNoContent());
		pushDispatcher.drain();

		assertThat(pushSender.sent()).hasSize(1);
		Sent sent = pushSender.sent().get(0);
		assertThat(sent.fids()).containsExactly(LEE_PHONE);
		assertThat(sent.message().data()).isEqualTo(Map.of("kind", "GUESTBOOK_RESTRICTED", "title",
				"남긴 방명록 글이 관리자에 의해 제한됐어요", "url", "/me", "tag", "ohjumwhat-guestbook-restricted"));
	}

	private void turnOn(User user, String fid) throws Exception {
		registerPushDevice(user, loginSession(user), fid).andExpect(status().isOk());
	}
}
