package com.ohjumwhat.letter;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.user.User;

/** 익명 쪽지: 보낸 사람이 응답·차단·답장 어디로도 드러나지 않는다. */
class LetterAnonymityIntegrationTest extends LetterTestBase {

	@Test
	void 받은_익명_쪽지의_응답에는_보낸_사람의_흔적이_없다() throws Exception {
		jdbcTemplate.update("update users set nickname = '박별명', photo_key = 'parkphotokey0001' where id = ?",
				park.getId());
		sendOk(park, devId, lee, "항상 고마워요", true);

		String json = receivedJson(lee);
		assertThat(json).doesNotContain("박민수", "박별명", "park@example.com", "parkphotokey0001", "sub-park")
			.doesNotContain("\"userId\":" + park.getId());
		received(lee).andExpect(jsonPath("$.letters[0].counterpart").value(nullValue()))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(true))
			.andExpect(jsonPath("$.letters[0].anonymous").value(true))
			.andExpect(jsonPath("$.letters[0].organization.name").value("개발팀"))
			.andExpect(jsonPath("$.letters[0].canReply").value(true));
		// 보낸 사람에게는 받는 사람이 보이고 「익명으로 보냄」이다.
		sent(park).andExpect(jsonPath("$.letters[0].counterpart.name").value("이영희"))
			.andExpect(jsonPath("$.letters[0].anonymous").value(true))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(false));
	}

	@Test
	void 익명_쪽지에_답장하면_답장한_사람에게도_받는_사람이_숨겨지고_다시_답하면_익명이_강제된다() throws Exception {
		long anonymous = sendOk(park, devId, lee, "누구게요", true);

		// 이영희가 답장: 받는 사람(박)은 이영희의 보낸 쪽지함에서도 「익명」이다.
		long answer = replyOk(lee, anonymous, "고마워요! 누구신지 궁금해요", false);
		String leeSent = sent(lee).andReturn().getResponse().getContentAsString();
		assertThat(leeSent).doesNotContain("박민수", "\"userId\":" + park.getId());
		sent(lee).andExpect(jsonPath("$.letters[0].counterpart").value(nullValue()))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(true))
			.andExpect(jsonPath("$.letters[0].replyTo.preview").value("누구게요"));
		// 박은 이영희의 답장을 실명으로 받는다.
		received(park).andExpect(jsonPath("$.letters[0].id").value(answer))
			.andExpect(jsonPath("$.letters[0].counterpart.name").value("이영희"));

		// 박이 다시 답하면 익명을 끄고 보내도 익명으로 간다(화면은 받은 쪽지의 replyAnonymous로 체크를 잠근다).
		received(park).andExpect(jsonPath("$.letters[0].replyAnonymous").value(true));
		received(lee).andExpect(jsonPath("$.letters[0].replyAnonymous").value(false));
		reply(park, answer, "비밀이에요", false).andExpect(status().isCreated())
			.andExpect(jsonPath("$.anonymous").value(true));
		String leeReceived = receivedJson(lee);
		assertThat(leeReceived).doesNotContain("박민수", "\"userId\":" + park.getId());
		received(lee).andExpect(jsonPath("$.letters[0].body").value("비밀이에요"))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(true));
	}

	@Test
	void 익명_쪽지에서_차단하면_그_사람의_익명_쪽지만_숨기고_실명_쪽지는_그대로다() throws Exception {
		long named = sendOk(park, devId, lee, "실명 쪽지", false);
		long anonymous = sendOk(park, devId, lee, "익명 쪽지", true);
		block(lee, anonymous);

		received(lee).andExpect(jsonPath("$.letters", hasSize(1))).andExpect(jsonPath("$.letters[0].id").value(named));
		// 차단 목록에는 이름 없이 「익명 쪽지를 보낸 사람」과 첫 줄만 있다.
		String blocks = mockMvc.perform(get("/api/letters/blocks").with(loginAs(lee)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].anonymous").value(true))
			.andExpect(jsonPath("$[0].person").value(nullValue()))
			.andExpect(jsonPath("$[0].preview").value("익명 쪽지"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(blocks).doesNotContain("박민수", "\"userId\":" + park.getId());

		// 새로 오는 실명 쪽지는 받고, 익명 쪽지는 받지 않는다(박에게는 보낸 것으로 보인다).
		long namedAgain = sendOk(park, devId, lee, "또 실명", false);
		sendOk(park, devId, lee, "또 익명", true);
		received(lee).andExpect(jsonPath("$.letters", hasSize(2)))
			.andExpect(jsonPath("$.letters[0].id").value(namedAgain));
		sent(park).andExpect(jsonPath("$.letters", hasSize(4)))
			.andExpect(jsonPath("$.letters[0].body").value("또 익명"))
			.andExpect(jsonPath("$.letters[0].readAt").value(nullValue()));
	}

	@Test
	void 실명_쪽지에서_차단하면_그_사람의_지난_익명_쪽지는_숨기지_않지만_새_익명_쪽지는_받지_않는다() throws Exception {
		long anonymous = sendOk(park, devId, lee, "예전 익명", true);
		long named = sendOk(park, devId, lee, "실명", false);
		block(lee, named);

		received(lee).andExpect(jsonPath("$.letters", hasSize(1)))
			.andExpect(jsonPath("$.letters[0].id").value(anonymous));
		// 실명 차단을 익명으로 우회하지 못한다.
		sendOk(park, devId, lee, "익명으로 우회", true);
		sendOk(park, devId, lee, "실명 다시", false);
		received(lee).andExpect(jsonPath("$.letters", hasSize(1)));
		mockMvc.perform(get("/api/letters/unread").with(loginAs(lee))).andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void 답장할_수_없는_이유는_한_문구로_알린다() throws Exception {
		long fromPark = sendOk(park, devId, lee, "익명", true);
		long fromKim = sendOk(kim, devId, lee, "실명", false);
		organizationService.leave(devId, lee.getId());

		reply(lee, fromPark, "답", false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));
		reply(lee, fromKim, "답", false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));
	}

	private void block(User user, long letterId) throws Exception {
		mockMvc.perform(post("/api/letters/" + letterId + "/block").with(loginAs(user)).with(xsrf()))
			.andExpect(status().isNoContent());
	}
}
