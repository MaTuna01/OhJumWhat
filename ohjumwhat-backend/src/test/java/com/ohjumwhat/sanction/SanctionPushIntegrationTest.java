package com.ohjumwhat.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ohjumwhat.FakePushSenderConfiguration.Sent;

/** 제재 푸시: 커밋 뒤 본인에게, 문구는 제한 → 초기화 → 경고 순서로 정한다(사유·설명은 넣지 않는다). */
class SanctionPushIntegrationTest extends SanctionTestBase {

	static final String KIM_PHONE = "fid_kim_phone_000001";

	@BeforeEach
	void turnOnPush() throws Exception {
		registerPushDevice(kim, loginSession(kim), KIM_PHONE).andExpect(status().isOk());
	}

	@Test
	void 제재를_받은_사람에게_알린다() throws Exception {
		sanction(kim, "{\"restrictions\": [\"CHAT\"], \"days\": 7, \"reason\": \"ABUSE\", \"note\": \"욕설을 했어요\"}");
		pushDispatcher.drain();

		assertThat(pushSender.sent()).hasSize(1);
		Sent sent = pushSender.sent().get(0);
		assertThat(sent.fids()).containsExactly(KIM_PHONE);
		assertThat(sent.message().data()).isEqualTo(Map.of("kind", "SANCTION", "title", "관리자가 이용을 제한했어요", "url",
				"/me", "tag", "ohjumwhat-sanction"));
	}

	@Test
	void 문구는_제한_초기화_경고_순서로_정한다() throws Exception {
		fillProfile(kim);
		sanction(kim, "{\"restrictions\": [\"PROFILE\"], \"resets\": [\"NICKNAME\"], \"reason\": \"PROFILE\"}");
		sanction(kim, "{\"resets\": [\"PHOTO\"], \"reason\": \"PROFILE\"}");
		sanction(kim, "{\"reason\": \"ETC\"}");
		pushDispatcher.drain();

		assertThat(pushSender.sent()).extracting(sent -> sent.message().title())
			.containsExactly("관리자가 이용을 제한했어요", "관리자가 프로필을 초기화했어요", "관리자의 경고가 도착했어요");
	}

	@Test
	void 알리기_전에_제재가_없어졌으면_알리지_않는다() throws Exception {
		long sanctionId = restrict(kim, 7, Restriction.CHAT);
		jdbcTemplate.update("delete from user_sanctions where id = ?", sanctionId);
		pushDispatcher.drain();

		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void 걸지_못한_제재는_알리지_않는다() throws Exception {
		apply(admin, kim, "{\"restrictions\": [\"CHAT\"], \"days\": 2, \"reason\": \"ABUSE\"}")
			.andExpect(status().isBadRequest());
		pushDispatcher.drain();

		assertThat(pushDispatcher.size()).isZero();
		assertThat(pushSender.sent()).isEmpty();
	}
}
