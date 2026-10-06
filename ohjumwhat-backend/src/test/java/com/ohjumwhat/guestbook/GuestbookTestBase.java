package com.ohjumwhat.guestbook;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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

/** 방명록 테스트 공통: 개발팀(김·이·박)과 디자인팀(김·최). 이와 최는 같은 조직이 없다. */
abstract class GuestbookTestBase extends IntegrationTest {

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
		clock.set(2026, 10, 6, 12, 0); // 2026-10-06T03:00:00Z
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

	/** 조직이 없는 회원 */
	User stranger() {
		return userRepository.save(new User("sub-stranger", "stranger@example.com", "남남", null));
	}

	User admin() {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		return userRepository.save(admin);
	}

	/** 시계를 seconds초 뒤로 옮긴다. */
	void later(long seconds) {
		clock.set(clock.instant().plusSeconds(seconds));
	}

	ResultActions list(User viewer, User owner) throws Exception {
		return list(viewer, owner.getId(), 0);
	}

	ResultActions list(User viewer, Long ownerId, int page) throws Exception {
		return mockMvc.perform(get("/api/guestbook/users/" + ownerId).param("page", String.valueOf(page))
			.with(loginAs(viewer)));
	}

	/** body는 JSON 문자열 안에 그대로 넣는다(줄바꿈은 \\n으로). */
	ResultActions write(User author, Long ownerId, String body) throws Exception {
		return mockMvc.perform(post("/api/guestbook/users/" + ownerId).with(loginAs(author)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"%s\"}".formatted(body)));
	}

	ResultActions write(User author, User owner, String body) throws Exception {
		return write(author, owner.getId(), body);
	}

	/** 쓰고 새 글의 id. 다음 글을 바로 쓸 수 있게 시계를 도배 방지 기간(5초)만큼 넘긴다. */
	long writeOk(User author, User owner, String body) throws Exception {
		String json = write(author, owner, body).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		later(5);
		return ((Number) JsonPath.read(json, "$.entries[0].id")).longValue();
	}

	ResultActions deleteEntry(User user, long entryId) throws Exception {
		return mockMvc.perform(delete("/api/guestbook/entries/" + entryId).with(loginAs(user)).with(xsrf()));
	}

	ResultActions report(User user, long entryId, String json) throws Exception {
		return mockMvc.perform(post("/api/guestbook/entries/" + entryId + "/report").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	ResultActions alerts(User user) throws Exception {
		return mockMvc.perform(get("/api/guestbook/alerts").with(loginAs(user)));
	}

	ResultActions seen(User user, Instant until) throws Exception {
		return mockMvc.perform(post("/api/guestbook/seen").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"until\": \"%s\"}".formatted(until)));
	}

	ResultActions ack(User user, Instant until) throws Exception {
		return mockMvc.perform(post("/api/guestbook/warnings/ack").with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"until\": \"%s\"}".formatted(until)));
	}

	ResultActions adminReports(User admin, String status) throws Exception {
		return mockMvc.perform(get("/api/admin/guestbook-reports").param("status", status).with(loginAs(admin)));
	}

	/** 그 글의 신고 id(관리자 목록에서 찾는다) */
	long reportIdOf(User admin, long entryId) throws Exception {
		String json = adminReports(admin, "all").andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(json, "$[?(@.entryId == " + entryId + ")].id");
		return ids.get(0).longValue();
	}

	ResultActions restrict(User admin, long reportId) throws Exception {
		return mockMvc.perform(post("/api/admin/guestbook-reports/" + reportId + "/restrict").with(loginAs(admin))
			.with(xsrf()));
	}

	ResultActions dismiss(User admin, long reportId) throws Exception {
		return mockMvc.perform(post("/api/admin/guestbook-reports/" + reportId + "/dismiss").with(loginAs(admin))
			.with(xsrf()));
	}
}
