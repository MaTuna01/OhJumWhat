package com.ohjumwhat.letter;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.user.User;

class LetterIntegrationTest extends LetterTestBase {

	@Test
	void 같은_조직_멤버에게_보내면_받은_쪽지함과_보낸_쪽지함에_보인다() throws Exception {
		send(kim, devId, lee, "  오늘 점심 같이 가요!\\n11시 50분에 1층에서 봬요  ", false)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.box").value("SENT"))
			.andExpect(jsonPath("$.counterpart.name").value("이영희"))
			.andExpect(jsonPath("$.organization.name").value("개발팀"))
			.andExpect(jsonPath("$.body").value("오늘 점심 같이 가요!\n11시 50분에 1층에서 봬요"))
			.andExpect(jsonPath("$.readAt").value(nullValue()));

		received(lee).andExpect(jsonPath("$.letters", hasSize(1)))
			.andExpect(jsonPath("$.letters[0].box").value("RECEIVED"))
			.andExpect(jsonPath("$.letters[0].counterpart.userId").value(kim.getId()))
			.andExpect(jsonPath("$.letters[0].counterpart.name").value("김철수"))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(false))
			.andExpect(jsonPath("$.letters[0].canReply").value(true))
			.andExpect(jsonPath("$.letters[0].reported").value(false))
			.andExpect(jsonPath("$.hasMore").value(false));
		sent(kim).andExpect(jsonPath("$.letters", hasSize(1)))
			.andExpect(jsonPath("$.letters[0].counterpart.name").value("이영희"))
			.andExpect(jsonPath("$.letters[0].canReply").value(false));
		received(kim).andExpect(jsonPath("$.letters", hasSize(0)));
	}

	@Test
	void 받는_사람이_읽으면_안_읽은_수가_줄고_보낸_사람에게_읽음이_보인다() throws Exception {
		long first = sendOk(kim, devId, lee, "하나", false);
		sendOk(park, devId, lee, "둘", true);
		mockMvc.perform(get("/api/letters/unread").with(loginAs(lee))).andExpect(jsonPath("$.count").value(2));

		// 보낸 사람은 읽음 처리를 할 수 없다(받은 쪽지가 아니다).
		mockMvc.perform(put("/api/letters/" + first + "/read").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/letters/" + first + "/read").with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/letters/unread").with(loginAs(lee))).andExpect(jsonPath("$.count").value(1));
		sent(kim).andExpect(jsonPath("$.letters[0].readAt").isNotEmpty());
	}

	@Test
	void 내용과_받는_사람을_확인한다() throws Exception {
		send(kim, devId, lee, "   ", false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));
		send(kim, devId, lee, "가".repeat(501), false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("500자 이하로 입력해 주세요."));
		send(kim, devId, lee, "삐\\u0007", false).andExpect(status().isBadRequest());
		send(kim, devId, kim, "나에게", false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("나에게는 쪽지를 보낼 수 없어요."));
		// 최는 개발팀 멤버가 아니다(디자인팀에서는 보낼 수 있다).
		send(kim, devId, choi, "안녕하세요", false).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("받는 사람을 찾을 수 없어요."));
		send(kim, designId, choi, "안녕하세요", false).andExpect(status().isCreated());
		// 내가 멤버가 아닌 조직에서는 보낼 수 없다.
		send(lee, designId, choi, "안녕하세요", false).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("조직을 찾을 수 없어요."));
		send(kim, devId, lee, "가".repeat(500), false).andExpect(status().isCreated());
	}

	@Test
	void 한_사람이_10분에_10통까지_보낸다() throws Exception {
		for (int i = 0; i < 10; i++) {
			send(kim, devId, lee, "쪽지 " + i, false).andExpect(status().isCreated());
		}
		send(kim, devId, lee, "열한 번째", false).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.message").value("쪽지를 너무 많이 보냈어요. 잠시 후 다시 보내 주세요."));
		// 답장도 같은 한도를 쓴다. 다른 사람은 따로 센다.
		send(lee, devId, kim, "답", false).andExpect(status().isCreated());

		clock.set(clock.instant().plus(Duration.ofMinutes(10)));
		send(kim, devId, lee, "10분 뒤", false).andExpect(status().isCreated());
	}

	@Test
	void 스무_통씩_나눠서_받는다() throws Exception {
		for (int i = 1; i <= 21; i++) {
			if (i % 10 == 1 && i > 1) {
				clock.set(clock.instant().plus(Duration.ofMinutes(10)));
			}
			sendOk(kim, devId, lee, "쪽지 " + i, false);
		}
		String json = receivedJson(lee);
		long last = ((Number) JsonPath.read(json, "$.letters[19].id")).longValue();
		received(lee).andExpect(jsonPath("$.letters", hasSize(20)))
			.andExpect(jsonPath("$.letters[0].body").value("쪽지 21"))
			.andExpect(jsonPath("$.hasMore").value(true));
		mockMvc.perform(get("/api/letters?box=received&before=" + last).with(loginAs(lee)))
			.andExpect(jsonPath("$.letters", hasSize(1)))
			.andExpect(jsonPath("$.letters[0].body").value("쪽지 1"))
			.andExpect(jsonPath("$.hasMore").value(false));
		mockMvc.perform(get("/api/letters?box=trash").with(loginAs(lee))).andExpect(status().isBadRequest());
	}

	@Test
	void 지우면_내_쪽지함에서만_사라진다() throws Exception {
		long id = sendOk(kim, devId, lee, "지울 쪽지", false);

		mockMvc.perform(delete("/api/letters/" + id).with(loginAs(lee)).with(xsrf())).andExpect(status().isNoContent());
		received(lee).andExpect(jsonPath("$.letters", hasSize(0)));
		sent(kim).andExpect(jsonPath("$.letters", hasSize(1)));
		mockMvc.perform(get("/api/letters/unread").with(loginAs(lee))).andExpect(jsonPath("$.count").value(0));

		mockMvc.perform(delete("/api/letters/" + id).with(loginAs(kim)).with(xsrf())).andExpect(status().isNoContent());
		sent(kim).andExpect(jsonPath("$.letters", hasSize(0)));
		// 이미 지운 쪽지, 남의 쪽지는 없다.
		mockMvc.perform(delete("/api/letters/" + id).with(loginAs(kim)).with(xsrf())).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/letters/" + id).with(loginAs(park)).with(xsrf())).andExpect(status().isNotFound());
	}

	@Test
	void 답장은_원래_보낸_사람에게_가고_원래_쪽지의_첫_줄을_보여준다() throws Exception {
		long id = sendOk(kim, devId, lee, "\\n  오늘 점심 같이 가요! 마라탕 어때요? 국물이 시원하고 맵기도 고를 수 있어요 정말로\\n두 번째 줄", false);

		reply(lee, id, "좋아요").andExpect(status().isCreated())
			.andExpect(jsonPath("$.counterpart.name").value("김철수"))
			.andExpect(jsonPath("$.replyTo.id").value(id))
			.andExpect(jsonPath("$.replyTo.preview").value("오늘 점심 같이 가요! 마라탕 어때요? 국물이 시원하고 맵기도 고를 수 …"));
		received(kim).andExpect(jsonPath("$.letters[0].body").value("좋아요"))
			.andExpect(jsonPath("$.letters[0].counterpart.name").value("이영희"))
			.andExpect(jsonPath("$.letters[0].replyTo.id").value(id));
		// 보낸 쪽지에는 답장할 수 없다.
		reply(kim, id, "내 쪽지에 답장").andExpect(status().isNotFound());
	}

	@Test
	void 내가_조직을_떠났거나_조직이_없어졌으면_답장할_수_없다() throws Exception {
		long id = sendOk(kim, devId, lee, "안녕", false);
		organizationService.leave(devId, lee.getId());

		received(lee).andExpect(jsonPath("$.letters[0].canReply").value(false))
			.andExpect(jsonPath("$.letters[0].organization.name").value("개발팀"));
		reply(lee, id, "답장").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));

		// 조직이 없어져도 쪽지는 남고 「삭제된 조직」(organization null)이다.
		long designLetter = sendOk(choi, designId, kim, "디자인팀에서", false);
		organizationService.leave(designId, choi.getId());
		organizationService.leave(designId, kim.getId());
		received(kim).andExpect(jsonPath("$.letters[0].id").value(designLetter))
			.andExpect(jsonPath("$.letters[0].organization").value(nullValue()))
			.andExpect(jsonPath("$.letters[0].canReply").value(false));
	}

	@Test
	void 상대가_조직을_떠나도_내가_멤버면_답장할_수_있다() throws Exception {
		long id = sendOk(park, devId, lee, "저 다음 주에 팀을 옮겨요", false);
		organizationService.leave(devId, park.getId());

		received(lee).andExpect(jsonPath("$.letters[0].canReply").value(true));
		reply(lee, id, "그동안 고마웠어요").andExpect(status().isCreated());
		received(park).andExpect(jsonPath("$.letters[0].body").value("그동안 고마웠어요"));
	}

	@Test
	void 강제_탈퇴하면_그_사람이_보내거나_받은_쪽지는_탈퇴한_사용자로_남는다() throws Exception {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		admin = userRepository.save(admin);
		long fromPark = sendOk(park, devId, lee, "박이 보낸 쪽지", false);
		long anonymousFromPark = sendOk(park, devId, lee, "박이 익명으로 보낸 쪽지", true);
		long toPark = sendOk(lee, devId, park, "박에게 보낸 쪽지", false);

		mockMvc.perform(delete("/api/admin/users/" + park.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		received(lee).andExpect(jsonPath("$.letters", hasSize(2)))
			.andExpect(jsonPath("$.letters[1].id").value(fromPark))
			.andExpect(jsonPath("$.letters[1].counterpart").value(nullValue()))
			.andExpect(jsonPath("$.letters[1].counterpartHidden").value(false))
			.andExpect(jsonPath("$.letters[1].canReply").value(false))
			// 익명 쪽지는 보낸 사람이 탈퇴해도 canReply가 바뀌지 않는다(바뀌면 누가 보냈는지 짐작하게 한다). 답장하면 같은 문구다.
			.andExpect(jsonPath("$.letters[0].id").value(anonymousFromPark))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(true))
			.andExpect(jsonPath("$.letters[0].canReply").value(true));
		// 박이 받은 쪽지도 지워지지 않고 이영희의 보낸 쪽지함에 「탈퇴한 사용자」로 남는다.
		sent(lee).andExpect(jsonPath("$.letters", hasSize(1)))
			.andExpect(jsonPath("$.letters[0].id").value(toPark))
			.andExpect(jsonPath("$.letters[0].counterpart").value(nullValue()))
			.andExpect(jsonPath("$.letters[0].counterpartHidden").value(false));
		reply(lee, fromPark, "답장").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));
		reply(lee, anonymousFromPark, "답장").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("답장할 수 없는 쪽지예요."));
	}

	@Test
	void 로그인하지_않으면_쪽지를_쓸_수_없다() throws Exception {
		mockMvc.perform(get("/api/letters")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/letters/unread")).andExpect(status().isUnauthorized());
	}
}
