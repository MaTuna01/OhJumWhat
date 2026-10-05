package com.ohjumwhat.letter;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.user.User;

/** 차단·신고와 관리자 콘솔의 신고 처리 */
class LetterBlockReportIntegrationTest extends LetterTestBase {

	@Test
	void 차단하면_지난_쪽지는_숨기고_새_쪽지는_받지_않으며_풀면_지난_쪽지만_돌아온다() throws Exception {
		long before = sendOk(park, devId, lee, "차단 전", false);
		block(lee, before).andExpect(status().isNoContent());
		// 다시 차단해도 그대로다.
		block(lee, before).andExpect(status().isNoContent());
		received(lee).andExpect(jsonPath("$.letters", hasSize(0)));

		sendOk(park, devId, lee, "차단 중", false);
		received(lee).andExpect(jsonPath("$.letters", hasSize(0)));
		sent(park).andExpect(jsonPath("$.letters", hasSize(2)));
		mockMvc.perform(get("/api/letters/unread").with(loginAs(lee))).andExpect(jsonPath("$.count").value(0));

		String blocks = mockMvc.perform(get("/api/letters/blocks").with(loginAs(lee)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].anonymous").value(false))
			.andExpect(jsonPath("$[0].person.name").value("박민수"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long blockId = ((Number) JsonPath.read(blocks, "$[0].id")).longValue();

		// 남의 차단은 풀 수 없다.
		mockMvc.perform(delete("/api/letters/blocks/" + blockId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/letters/blocks/" + blockId).with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isNoContent());
		received(lee).andExpect(jsonPath("$.letters", hasSize(1))).andExpect(jsonPath("$.letters[0].id").value(before));
	}

	@Test
	void 받은_사람만_차단하고_신고한다() throws Exception {
		long id = sendOk(park, devId, lee, "쪽지", false);
		block(park, id).andExpect(status().isNotFound());
		block(kim, id).andExpect(status().isNotFound());
		report(park, id, "{}").andExpect(status().isNotFound());
	}

	@Test
	void 신고하면_관리자는_익명이어도_실제_보낸_사람을_보고_처리한다() throws Exception {
		User admin = admin();
		long id = sendOk(park, devId, lee, "점심 메뉴 좀 그만 올리세요", true);

		report(lee, id, "{\"reason\": \"  기분 나쁜 말이 있어요  \", \"block\": true}").andExpect(status().isNoContent());
		// 다시 신고해도 그대로다.
		report(lee, id, "{\"reason\": \"다른 사유\"}").andExpect(status().isNoContent());
		received(lee).andExpect(jsonPath("$.letters", hasSize(0)));
		mockMvc.perform(get("/api/letters/blocks").with(loginAs(lee)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].anonymous").value(true));

		mockMvc.perform(get("/api/admin/stats").with(loginAs(admin))).andExpect(jsonPath("$.openReportCount").value(1));
		String reports = mockMvc.perform(get("/api/admin/letter-reports").with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].reason").value("기분 나쁜 말이 있어요"))
			.andExpect(jsonPath("$[0].body").value("점심 메뉴 좀 그만 올리세요"))
			.andExpect(jsonPath("$[0].anonymous").value(true))
			.andExpect(jsonPath("$[0].senderId").value(park.getId()))
			.andExpect(jsonPath("$[0].senderName").value("박민수"))
			.andExpect(jsonPath("$[0].senderEmail").value("park@example.com"))
			.andExpect(jsonPath("$[0].recipientName").value("이영희"))
			.andExpect(jsonPath("$[0].organizationName").value("개발팀"))
			.andExpect(jsonPath("$[0].resolvedAt").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long reportId = ((Number) JsonPath.read(reports, "$[0].id")).longValue();

		mockMvc.perform(post("/api/admin/letter-reports/" + reportId + "/resolve").with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/admin/letter-reports").with(loginAs(admin))).andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/admin/letter-reports?status=all").with(loginAs(admin)))
			.andExpect(jsonPath("$[0].resolvedAt").isNotEmpty())
			.andExpect(jsonPath("$[0].resolvedByName").value("관리자"));
		mockMvc.perform(get("/api/admin/stats").with(loginAs(admin))).andExpect(jsonPath("$.openReportCount").value(0));
	}

	@Test
	void 받은_사람이_쪽지를_지워도_신고는_남는다() throws Exception {
		User admin = admin();
		long id = sendOk(park, devId, lee, "쪽지", false);
		report(lee, id, "{}").andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/letters/" + id).with(loginAs(lee)).with(xsrf())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/admin/letter-reports").with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].reason").value(nullValue()))
			.andExpect(jsonPath("$[0].body").value("쪽지"));
	}

	@Test
	void 신고한_쪽지는_신고함으로_보이고_사유는_100자까지다() throws Exception {
		long id = sendOk(park, devId, lee, "쪽지", false);
		report(lee, id, "{\"reason\": \"" + "가".repeat(101) + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("100자 이하로 입력해 주세요."));
		report(lee, id, "{\"reason\": \"" + "가".repeat(100) + "\"}").andExpect(status().isNoContent());
		received(lee).andExpect(jsonPath("$.letters[0].reported").value(true));
	}

	@Test
	void 보낸_사람을_강제_탈퇴하면_열린_신고는_처리_완료가_된다() throws Exception {
		User admin = admin();
		long id = sendOk(park, devId, lee, "쪽지", true);
		report(lee, id, "{}").andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/admin/users/" + park.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/admin/stats").with(loginAs(admin))).andExpect(jsonPath("$.openReportCount").value(0));
		mockMvc.perform(get("/api/admin/letter-reports?status=all").with(loginAs(admin)))
			.andExpect(jsonPath("$[0].senderId").value(nullValue()))
			.andExpect(jsonPath("$[0].resolvedByName").value("관리자"));
	}

	@Test
	void 관리자가_아니면_신고를_볼_수_없다() throws Exception {
		mockMvc.perform(get("/api/admin/letter-reports").with(loginAs(lee))).andExpect(status().isForbidden());
	}

	private User admin() {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		return userRepository.save(admin);
	}

	private ResultActions block(User user, long letterId) throws Exception {
		return mockMvc.perform(post("/api/letters/" + letterId + "/block").with(loginAs(user)).with(xsrf()));
	}

	private ResultActions report(User user, long letterId, String json) throws Exception {
		return mockMvc.perform(post("/api/letters/" + letterId + "/report").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}
}
