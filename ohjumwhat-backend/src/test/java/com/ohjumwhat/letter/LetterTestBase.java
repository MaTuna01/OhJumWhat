package com.ohjumwhat.letter;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 쪽지 테스트 공통: 개발팀(김·이·박)과 디자인팀(김·최) */
abstract class LetterTestBase extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	User kim;

	User lee;

	User park;

	User choi;

	Long devId;

	Long designId;

	@BeforeEach
	void setUpOrganizations() {
		clock.set(2026, 10, 5, 12, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		choi = userRepository.save(new User("sub-choi", "choi@example.com", "최준호", null));
		var dev = organizationService.create(kim.getId(), "개발팀");
		devId = dev.id();
		inviteService.join(dev.inviteToken(), lee.getId());
		inviteService.join(dev.inviteToken(), park.getId());
		var design = organizationService.create(kim.getId(), "디자인팀");
		designId = design.id();
		inviteService.join(design.inviteToken(), choi.getId());
	}

	ResultActions send(User from, Long organizationId, User to, String body, boolean anonymous) throws Exception {
		return mockMvc.perform(post("/api/letters").with(loginAs(from)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"organizationId": %d, "recipientId": %d, "body": "%s", "anonymous": %s}"""
				.formatted(organizationId, to.getId(), body, anonymous)));
	}

	/** 보내고 새 쪽지의 id */
	long sendOk(User from, Long organizationId, User to, String body, boolean anonymous) throws Exception {
		String json = send(from, organizationId, to, body, anonymous).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	/** 답장(익명 여부는 서버가 정한다) */
	ResultActions reply(User from, long letterId, String body) throws Exception {
		return mockMvc.perform(post("/api/letters/" + letterId + "/reply").with(loginAs(from)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"%s\"}".formatted(body)));
	}

	long replyOk(User from, long letterId, String body) throws Exception {
		String json = reply(from, letterId, body).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	ResultActions received(User user) throws Exception {
		return mockMvc.perform(get("/api/letters?box=received").with(loginAs(user)));
	}

	ResultActions sent(User user) throws Exception {
		return mockMvc.perform(get("/api/letters?box=sent").with(loginAs(user)));
	}

	String receivedJson(User user) throws Exception {
		return received(user).andReturn().getResponse().getContentAsString();
	}
}
