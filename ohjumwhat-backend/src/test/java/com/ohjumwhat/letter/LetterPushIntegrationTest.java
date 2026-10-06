package com.ohjumwhat.letter;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.FakePushSenderConfiguration.Sent;
import com.ohjumwhat.user.User;

/** 쪽지 푸시: 받은 쪽지함과 같은 이름 규칙, 익명 보호, 차단, 기기 정리 */
class LetterPushIntegrationTest extends LetterTestBase {

	static final String LEE_PHONE = "fid_lee_phone_000001";

	static final String LEE_LAPTOP = "fid_lee_laptop_00002";

	static final String KIM_PHONE = "fid_kim_phone_000003";

	@Test
	void 실명_쪽지는_보낸_사람_이름으로_알린다() throws Exception {
		turnOn(lee, LEE_PHONE);

		sendOk(kim, devId, lee, "점심 같이 먹어요", false);
		pushDispatcher.drain();

		assertThat(pushSender.sent()).hasSize(1);
		Sent sent = pushSender.sent().get(0);
		assertThat(sent.fids()).containsExactly(LEE_PHONE);
		assertThat(sent.message().data()).isEqualTo(Map.of("kind", "LETTER", "title", "김철수님이 쪽지를 보냈어요", "url",
				"/letters", "tag", "ohjumwhat-letter"));
	}

	@Test
	void 별명이_있으면_별명으로_알린다() throws Exception {
		jdbcTemplate.update("update users set nickname = '철수별명' where id = ?", kim.getId());
		turnOn(lee, LEE_PHONE);

		sendOk(kim, devId, lee, "안녕하세요", false);
		pushDispatcher.drain();

		assertThat(titles()).containsExactly("철수별명님이 쪽지를 보냈어요");
	}

	@Test
	void 익명_쪽지는_data_어디에도_이름이_없다() throws Exception {
		jdbcTemplate.update("update users set nickname = '철수별명' where id = ?", kim.getId());
		turnOn(lee, LEE_PHONE);

		sendOk(kim, devId, lee, "익명으로 보내요", true);
		pushDispatcher.drain();

		Map<String, String> data = pushSender.sent().get(0).message().data();
		assertThat(data).isEqualTo(Map.of("kind", "LETTER", "title", "익명 쪽지가 왔어요", "url", "/letters", "tag",
				"ohjumwhat-letter"));
		assertThat(String.join(" ", data.values())).doesNotContain("김철수", "철수별명", "익명으로 보내요");
	}

	@Test
	void 본문은_싣지_않는다() throws Exception {
		turnOn(lee, LEE_PHONE);

		sendOk(kim, devId, lee, "비밀 이야기", false);
		pushDispatcher.drain();

		assertThat(String.join(" ", pushSender.sent().get(0).message().data().values())).doesNotContain("비밀 이야기");
	}

	@Test
	void 실명으로_차단한_사람의_쪽지는_알리지_않는다() throws Exception {
		turnOn(lee, LEE_PHONE);
		long first = sendOk(kim, devId, lee, "첫 쪽지", false);
		pushDispatcher.drain();
		block(lee, first);

		sendOk(kim, devId, lee, "실명 쪽지", false);
		sendOk(kim, devId, lee, "익명 쪽지", true);
		assertThat(pushDispatcher.size()).isZero();
		pushDispatcher.drain();

		assertThat(titles()).containsExactly("김철수님이 쪽지를 보냈어요");
	}

	@Test
	void 익명_쪽지를_한_통_차단하면_그_사람의_익명_쪽지는_알리지_않고_실명_쪽지는_알린다() throws Exception {
		turnOn(lee, LEE_PHONE);
		long anonymous = sendOk(kim, devId, lee, "익명 쪽지", true);
		pushDispatcher.drain();
		block(lee, anonymous);

		sendOk(kim, devId, lee, "다시 익명 쪽지", true);
		pushDispatcher.drain();
		assertThat(titles()).containsExactly("익명 쪽지가 왔어요");

		sendOk(kim, devId, lee, "실명 쪽지", false);
		pushDispatcher.drain();
		assertThat(titles()).containsExactly("익명 쪽지가 왔어요", "김철수님이 쪽지를 보냈어요");
	}

	@Test
	void 답장은_답장으로_알리고_익명으로_이어진_답장은_이름_없이_알린다() throws Exception {
		turnOn(lee, LEE_PHONE);
		turnOn(kim, KIM_PHONE);

		// 실명 쪽지에 답장
		long named = sendOk(kim, devId, lee, "실명 쪽지", false);
		replyOk(lee, named, "실명 답장");
		// 익명 쪽지에 답장하면 실명이고(이가 쓴 답장), 그 답장에 김이 다시 답하면 익명이다.
		long anonymous = sendOk(kim, devId, lee, "익명 쪽지", true);
		long replyToAnonymous = replyOk(lee, anonymous, "익명 쪽지에 답장");
		replyOk(kim, replyToAnonymous, "다시 익명으로");
		pushDispatcher.drain();

		assertThat(pushSender.sent()).extracting(sent -> sent.fids().get(0) + " " + sent.message().title())
			.containsExactly(LEE_PHONE + " 김철수님이 쪽지를 보냈어요", KIM_PHONE + " 이영희님이 답장을 보냈어요",
					LEE_PHONE + " 익명 쪽지가 왔어요", KIM_PHONE + " 이영희님이 답장을 보냈어요", LEE_PHONE + " 익명 쪽지가 왔어요");
	}

	@Test
	void 기기가_없으면_보내지_않는다() throws Exception {
		sendOk(kim, devId, lee, "안녕하세요", false);
		assertThat(pushDispatcher.size()).isEqualTo(1);
		pushDispatcher.drain();

		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void 푸시가_꺼져_있으면_작업도_맡기지_않는다() throws Exception {
		turnOn(lee, LEE_PHONE);
		pushSender.setEnabled(false);

		sendOk(kim, devId, lee, "안녕하세요", false);

		assertThat(pushDispatcher.size()).isZero();
	}

	@Test
	void 받기_전에_지운_쪽지는_알리지_않는다() throws Exception {
		turnOn(lee, LEE_PHONE);
		long letter = sendOk(kim, devId, lee, "안녕하세요", false);
		mockMvc.perform(delete("/api/letters/" + letter).with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isNoContent());

		pushDispatcher.drain();

		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void 더는_받을_수_없는_기기는_지운다() throws Exception {
		turnOn(lee, LEE_PHONE);
		turnOn(lee, LEE_LAPTOP);
		pushSender.markStale(LEE_PHONE);

		sendOk(kim, devId, lee, "안녕하세요", false);
		pushDispatcher.drain();

		assertThat(pushSender.sent().get(0).fids()).containsExactlyInAnyOrder(LEE_PHONE, LEE_LAPTOP);
		assertThat(fids()).containsExactly(LEE_LAPTOP);
	}

	@Test
	void 보내기가_실패해도_쪽지는_보내지고_기기는_남는다() throws Exception {
		turnOn(lee, LEE_PHONE);
		pushSender.failWith(new IllegalStateException("FCM 장애"));

		sendOk(kim, devId, lee, "안녕하세요", false);
		assertThatCode(() -> pushDispatcher.drain()).doesNotThrowAnyException();

		assertThat(fids()).containsExactly(LEE_PHONE);
		assertThat(receivedJson(lee)).contains("안녕하세요");
	}

	/** 그 회원의 로그인 세션으로 이 기기에서 알림을 켠다. */
	private void turnOn(User user, String fid) throws Exception {
		registerPushDevice(user, loginSession(user), fid).andExpect(status().isOk());
	}

	private void block(User user, long letterId) throws Exception {
		mockMvc.perform(post("/api/letters/" + letterId + "/block").with(loginAs(user)).with(xsrf()))
			.andExpect(status().is2xxSuccessful());
	}

	private List<String> titles() {
		return pushSender.sent().stream().map(sent -> sent.message().title()).toList();
	}

	private List<String> fids() {
		return jdbcTemplate.queryForList("select fid from push_devices order by id", String.class);
	}
}
