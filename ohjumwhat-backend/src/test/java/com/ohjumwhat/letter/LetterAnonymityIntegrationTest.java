package com.ohjumwhat.letter;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
		long answer = replyOk(lee, anonymous, "고마워요! 누구신지 궁금해요");
		String leeSent = sent(lee).andReturn().getResponse().getContentAsString();
		assertThat(leeSent).doesNotContain("박민수", "\"userId\":" + park.getId());
		sent(lee).andExpect(jsonPath("$.letters[0].counterpart").value(nullValue()))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(true))
			.andExpect(jsonPath("$.letters[0].replyTo.preview").value("누구게요"));
		// 박은 이영희의 답장을 실명으로 받는다(박은 자기가 누구에게 보냈는지 알기 때문에 답장은 익명이 될 수 없다).
		received(park).andExpect(jsonPath("$.letters[0].id").value(answer))
			.andExpect(jsonPath("$.letters[0].counterpart.name").value("이영희"))
			.andExpect(jsonPath("$.letters[0].anonymous").value(false));

		// 박이 다시 답하면 익명으로 간다(화면은 받은 쪽지의 replyAnonymous로 체크를 잠근다).
		received(park).andExpect(jsonPath("$.letters[0].replyAnonymous").value(true));
		received(lee).andExpect(jsonPath("$.letters[0].replyAnonymous").value(false));
		reply(park, answer, "비밀이에요").andExpect(status().isCreated())
			.andExpect(jsonPath("$.anonymous").value(true));
		String leeReceived = receivedJson(lee);
		assertThat(leeReceived).doesNotContain("박민수", "\"userId\":" + park.getId());
		received(lee).andExpect(jsonPath("$.letters[0].body").value("비밀이에요"))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(true));
	}

	@Test
	void 답장은_익명을_요청해도_실명으로_간다() throws Exception {
		// 박이 이영희에게 보낸 쪽지에 온 답장은 이영희가 쓴 것이 뻔하다.
		long named = sendOk(park, devId, lee, "점심 뭐 먹어요?", false);
		mockMvc.perform(post("/api/letters/" + named + "/reply").with(loginAs(lee)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"비밀\", \"anonymous\": true}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.anonymous").value(false));
		received(park).andExpect(jsonPath("$.letters[0].counterpart.name").value("이영희"))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(false));
	}

	@Test
	void 익명_차단은_그_쪽지만_숨겨서_같은_사람이_쓴_익명_쪽지인지_드러나지_않는다() throws Exception {
		// 이영희가 박에게 익명 쪽지 두 통, 김이 한 통. 박이 이영희의 한 통을 차단해도 이영희의 다른 익명 쪽지는 그대로 보인다.
		long first = sendOk(lee, devId, park, "첫 번째 익명", true);
		long second = sendOk(lee, devId, park, "두 번째 익명", true);
		long fromKim = sendOk(kim, devId, park, "김의 익명", true);
		block(park, first);

		received(park).andExpect(jsonPath("$.letters", hasSize(2)))
			.andExpect(jsonPath("$.letters[0].id").value(fromKim))
			.andExpect(jsonPath("$.letters[1].id").value(second));
		mockMvc.perform(get("/api/letters/unread").with(loginAs(park))).andExpect(jsonPath("$.count").value(2));

		// 같은 사람의 익명 쪽지를 또 차단하면 차단 목록이 한 줄 늘어난다(줄 수로 같은 사람인지 알 수 없다). 다시 눌러도 그대로다.
		block(park, second);
		block(park, second);
		mockMvc.perform(get("/api/letters/blocks").with(loginAs(park)))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].preview").value("두 번째 익명"))
			.andExpect(jsonPath("$[1].preview").value("첫 번째 익명"));
		received(park).andExpect(jsonPath("$.letters", hasSize(1)))
			.andExpect(jsonPath("$.letters[0].id").value(fromKim));

		// 새로 오는 이영희의 익명 쪽지는 받지 않는다(이영희에게는 보낸 것으로 보인다).
		sendOk(lee, devId, park, "또 익명", true);
		received(park).andExpect(jsonPath("$.letters", hasSize(1)));
		sent(lee).andExpect(jsonPath("$.letters[0].body").value("또 익명"));
	}

	@Test
	void 익명_쪽지를_보낸_사람이_강제_탈퇴해도_익명_차단은_그대로다() throws Exception {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		admin = userRepository.save(admin);
		long blocked = sendOk(park, devId, lee, "차단할 익명", true);
		long later = sendOk(park, devId, lee, "나중에 차단할 익명", true);
		block(lee, blocked);

		mockMvc.perform(delete("/api/admin/users/" + park.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		// 차단한 쪽지가 다시 보이지 않고, 차단 목록도 그대로다(바뀌면 보낸 사람이 방금 탈퇴했다는 것이 드러난다).
		received(lee).andExpect(jsonPath("$.letters", hasSize(1))).andExpect(jsonPath("$.letters[0].id").value(later));
		mockMvc.perform(get("/api/letters/blocks").with(loginAs(lee))).andExpect(jsonPath("$", hasSize(1)));
		// 탈퇴한 사람의 익명 쪽지도 차단된다(탈퇴하지 않은 사람과 똑같이).
		block(lee, later);
		received(lee).andExpect(jsonPath("$.letters", hasSize(0)));
		mockMvc.perform(get("/api/letters/blocks").with(loginAs(lee)))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].preview").value("나중에 차단할 익명"));
	}

	@Test
	void 익명_쪽지에서_차단하면_그_쪽지만_숨기고_실명_쪽지는_그대로다() throws Exception {
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

		reply(lee, fromPark, "답").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));
		reply(lee, fromKim, "답").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));
	}

	private void block(User user, long letterId) throws Exception {
		mockMvc.perform(post("/api/letters/" + letterId + "/block").with(loginAs(user)).with(xsrf()))
			.andExpect(status().isNoContent());
	}
}
