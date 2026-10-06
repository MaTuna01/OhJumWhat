package com.ohjumwhat.guestbook;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.user.User;

/** 주인의 신고와 관리자 콘솔의 처리(글 제한·문제 없음), 강제 탈퇴 */
@RecordApplicationEvents
class GuestbookReportIntegrationTest extends GuestbookTestBase {

	@Autowired
	ApplicationEvents events;

	@Test
	void 주인만_신고하고_다시_신고해도_그대로다() throws Exception {
		User admin = admin();
		User stranger = stranger();
		long entry = writeOk(lee, kim, "이의 글");

		report(park, entry, "{}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("내 방명록의 글만 신고할 수 있어요."));
		report(lee, entry, "{}").andExpect(status().isForbidden());
		report(stranger, entry, "{}").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("방명록 글을 찾을 수 없어요."));
		report(kim, entry, "{\"reason\": \"" + "가".repeat(101) + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("100자 이하로 입력해 주세요."));

		report(kim, entry, "{\"reason\": \"  기분 나쁜 표현이에요  \"}").andExpect(status().isNoContent());
		report(kim, entry, "{\"reason\": \"다른 사유\"}").andExpect(status().isNoContent());

		adminReports(admin, "open").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].reason").value("기분 나쁜 표현이에요"))
			.andExpect(jsonPath("$[0].resolution").value(nullValue()))
			.andExpect(jsonPath("$[0].resolvedAt").value(nullValue()))
			.andExpect(jsonPath("$[0].entryId").value(entry))
			.andExpect(jsonPath("$[0].body").value("이의 글"))
			.andExpect(jsonPath("$[0].ownerId").value(kim.getId()))
			.andExpect(jsonPath("$[0].ownerName").value("김철수"))
			.andExpect(jsonPath("$[0].ownerEmail").value("kim@example.com"))
			.andExpect(jsonPath("$[0].authorId").value(lee.getId()))
			.andExpect(jsonPath("$[0].authorName").value("이영희"))
			.andExpect(jsonPath("$[0].authorEmail").value("lee@example.com"));
	}

	@Test
	void 사유는_선택이고_본문이_없어도_된다() throws Exception {
		User admin = admin();
		long first = writeOk(lee, kim, "하나");
		long second = writeOk(lee, kim, "둘");

		report(kim, first, "{\"reason\": \"   \"}").andExpect(status().isNoContent());
		mockMvc.perform(post("/api/guestbook/entries/" + second + "/report").with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());

		adminReports(admin, "open").andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].reason").value(nullValue()))
			.andExpect(jsonPath("$[1].reason").value(nullValue()));
	}

	@Test
	void 지운_글과_없는_글은_신고할_수_없다() throws Exception {
		long entry = writeOk(lee, kim, "이의 글");
		deleteEntry(lee, entry).andExpect(status().isNoContent());

		report(kim, entry, "{}").andExpect(status().isNotFound());
		report(kim, 9999L, "{}").andExpect(status().isNotFound());
	}

	@Test
	void 글을_제한하면_누구에게도_본문을_보내지_않고_관리자는_원문을_본다() throws Exception {
		User admin = admin();
		long entry = writeOk(lee, kim, "기분 나쁜 글");
		report(kim, entry, "{}").andExpect(status().isNoContent());
		long reportId = reportIdOf(admin, entry);

		restrict(admin, reportId).andExpect(status().isNoContent());

		list(kim, kim).andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.entries[0].body").value(nullValue()))
			.andExpect(jsonPath("$.entries[0].restricted").value(true))
			.andExpect(jsonPath("$.entries[0].canDelete").value(true))
			.andExpect(jsonPath("$.entries[0].canReport").value(false))
			.andExpect(jsonPath("$.entries[0].reported").value(true));
		list(lee, kim).andExpect(jsonPath("$.entries[0].body").value(nullValue()))
			.andExpect(jsonPath("$.entries[0].restricted").value(true))
			.andExpect(jsonPath("$.entries[0].mine").value(true))
			.andExpect(jsonPath("$.entries[0].canDelete").value(false));
		list(park, kim).andExpect(jsonPath("$.entries[0].body").value(nullValue()))
			.andExpect(jsonPath("$.entries[0].restricted").value(true))
			.andExpect(jsonPath("$.entries[0].author.name").value("이영희"));
		// 제한된 글은 다시 신고해도 그대로다.
		report(kim, entry, "{}").andExpect(status().isNoContent());

		adminReports(admin, "open").andExpect(jsonPath("$", hasSize(0)));
		adminReports(admin, "all").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].resolution").value("RESTRICTED"))
			.andExpect(jsonPath("$[0].resolvedAt").value("2026-10-06T03:00:05Z"))
			.andExpect(jsonPath("$[0].resolvedByName").value("관리자"))
			.andExpect(jsonPath("$[0].body").value("기분 나쁜 글"))
			.andExpect(jsonPath("$[0].restrictedAt").value("2026-10-06T03:00:05Z"))
			.andExpect(jsonPath("$[0].deletedAt").value(nullValue()))
			.andExpect(jsonPath("$[0].writtenAt").value("2026-10-06T03:00:00Z"));

		// 같은 결과로 다시 처리하면 그대로, 다른 결과로는 바꿀 수 없다.
		restrict(admin, reportId).andExpect(status().isNoContent());
		dismiss(admin, reportId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 처리한 신고예요."));
		assertThat(events.stream(GuestbookEntryRestrictedEvent.class))
			.containsExactly(new GuestbookEntryRestrictedEvent(entry, lee.getId(), kim.getId()));
	}

	@Test
	void 문제_없음으로_처리하면_글은_그대로다() throws Exception {
		User admin = admin();
		long entry = writeOk(lee, kim, "괜찮은 글");
		report(kim, entry, "{}").andExpect(status().isNoContent());
		long reportId = reportIdOf(admin, entry);

		dismiss(admin, reportId).andExpect(status().isNoContent());
		dismiss(admin, reportId).andExpect(status().isNoContent());
		restrict(admin, reportId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 처리한 신고예요."));

		list(park, kim).andExpect(jsonPath("$.entries[0].body").value("괜찮은 글"))
			.andExpect(jsonPath("$.entries[0].restricted").value(false));
		list(kim, kim).andExpect(jsonPath("$.entries[0].reported").value(true))
			.andExpect(jsonPath("$.entries[0].canReport").value(false));
		adminReports(admin, "all").andExpect(jsonPath("$[0].resolution").value("DISMISSED"))
			.andExpect(jsonPath("$[0].restrictedAt").value(nullValue()));
		assertThat(events.stream(GuestbookEntryRestrictedEvent.class)).isEmpty();
	}

	@Test
	void 제한된_글은_주인만_지우고_쓴_사람은_403이다() throws Exception {
		User admin = admin();
		long entry = writeOk(lee, kim, "기분 나쁜 글");
		report(kim, entry, "{}").andExpect(status().isNoContent());
		restrict(admin, reportIdOf(admin, entry)).andExpect(status().isNoContent());

		deleteEntry(lee, entry).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("관리자가 제한한 글은 방명록 주인만 지울 수 있어요."));
		list(kim, kim).andExpect(jsonPath("$.totalCount").value(1));

		deleteEntry(kim, entry).andExpect(status().isNoContent());
		list(kim, kim).andExpect(jsonPath("$.totalCount").value(0));
		// 주인이 치워도 제한 기록과 신고는 남는다.
		adminReports(admin, "all").andExpect(jsonPath("$[0].resolution").value("RESTRICTED"))
			.andExpect(jsonPath("$[0].deletedAt").value(notNullValue()));
	}

	@Test
	void 지운_글도_제한하고_쓴_사람에게_경고한다() throws Exception {
		User admin = admin();
		long entry = writeOk(lee, kim, "지우면 그만");
		report(kim, entry, "{}").andExpect(status().isNoContent());
		deleteEntry(lee, entry).andExpect(status().isNoContent());

		restrict(admin, reportIdOf(admin, entry)).andExpect(status().isNoContent());

		adminReports(admin, "all").andExpect(jsonPath("$[0].deletedAt").value("2026-10-06T03:00:05Z"))
			.andExpect(jsonPath("$[0].restrictedAt").value("2026-10-06T03:00:05Z"))
			.andExpect(jsonPath("$[0].body").value("지우면 그만"));
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(1)))
			.andExpect(jsonPath("$.warnings[0].entryId").value(entry))
			.andExpect(jsonPath("$.warnings[0].body").value("지우면 그만"));
		list(kim, kim).andExpect(jsonPath("$.entries", hasSize(0)));
	}

	@Test
	void 없는_신고는_404다() throws Exception {
		User admin = admin();
		restrict(admin, 9999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("신고를 찾을 수 없어요."));
		dismiss(admin, 9999L).andExpect(status().isNotFound());
	}

	@Test
	void 개요의_처리할_신고는_쪽지와_방명록을_합친다() throws Exception {
		User admin = admin();
		String letter = mockMvc.perform(post("/api/letters").with(loginAs(park)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"organizationId": %d, "recipientId": %d, "body": "쪽지", "anonymous": false}"""
				.formatted(devId, lee.getId())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long letterId = ((Number) JsonPath.read(letter, "$.id")).longValue();
		mockMvc.perform(post("/api/letters/" + letterId + "/report").with(loginAs(lee)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isNoContent());
		long first = writeOk(lee, kim, "하나");
		long second = writeOk(park, kim, "둘");
		report(kim, first, "{}").andExpect(status().isNoContent());
		report(kim, second, "{}").andExpect(status().isNoContent());

		stats(admin).andExpect(jsonPath("$.openReportCount").value(3))
			.andExpect(jsonPath("$.openLetterReportCount").value(1))
			.andExpect(jsonPath("$.openGuestbookReportCount").value(2));

		dismiss(admin, reportIdOf(admin, first)).andExpect(status().isNoContent());
		stats(admin).andExpect(jsonPath("$.openReportCount").value(2))
			.andExpect(jsonPath("$.openLetterReportCount").value(1))
			.andExpect(jsonPath("$.openGuestbookReportCount").value(1));
	}

	@Test
	void 관리자가_아니면_방명록_신고를_보거나_처리할_수_없다() throws Exception {
		long entry = writeOk(lee, kim, "이의 글");
		report(kim, entry, "{}").andExpect(status().isNoContent());

		adminReports(kim, "open").andExpect(status().isForbidden());
		restrict(kim, 1L).andExpect(status().isForbidden());
		dismiss(kim, 1L).andExpect(status().isForbidden());
	}

	@Test
	void 쓴_사람을_강제_탈퇴하면_처리_전_신고의_글을_제한하고_글은_탈퇴한_사용자로_남는다() throws Exception {
		User admin = admin();
		long reported = writeOk(lee, kim, "신고된 글");
		long dismissed = writeOk(lee, kim, "문제 없는 글");
		long other = writeOk(lee, park, "박에게 쓴 글");
		report(kim, reported, "{}").andExpect(status().isNoContent());
		report(kim, dismissed, "{}").andExpect(status().isNoContent());
		dismiss(admin, reportIdOf(admin, dismissed)).andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		stats(admin).andExpect(jsonPath("$.openGuestbookReportCount").value(0));
		String reports = adminReports(admin, "all").andReturn().getResponse().getContentAsString();
		assertThat(field(reports, reported, "resolution")).containsExactly("RESTRICTED");
		assertThat(field(reports, reported, "resolvedByName")).containsExactly("관리자");
		assertThat(field(reports, reported, "restrictedAt")).doesNotContainNull();
		assertThat(field(reports, reported, "authorId")).containsExactly((Object) null);
		assertThat(field(reports, reported, "authorName")).containsExactly((Object) null);
		assertThat(field(reports, reported, "body")).containsExactly("신고된 글");
		// 이미 처리한 신고는 그대로다.
		assertThat(field(reports, dismissed, "resolution")).containsExactly("DISMISSED");
		assertThat(field(reports, dismissed, "restrictedAt")).containsExactly((Object) null);

		list(kim, kim).andExpect(jsonPath("$.entries", hasSize(2)))
			.andExpect(jsonPath("$.entries[0].id").value(dismissed))
			.andExpect(jsonPath("$.entries[0].author").value(nullValue()))
			.andExpect(jsonPath("$.entries[0].body").value("문제 없는 글"))
			.andExpect(jsonPath("$.entries[0].restricted").value(false))
			.andExpect(jsonPath("$.entries[1].id").value(reported))
			.andExpect(jsonPath("$.entries[1].author").value(nullValue()))
			.andExpect(jsonPath("$.entries[1].body").value(nullValue()))
			.andExpect(jsonPath("$.entries[1].restricted").value(true));
		list(park, park).andExpect(jsonPath("$.entries[0].id").value(other))
			.andExpect(jsonPath("$.entries[0].author").value(nullValue()))
			.andExpect(jsonPath("$.entries[0].body").value("박에게 쓴 글"))
			.andExpect(jsonPath("$.entries[0].restricted").value(false));
		// 쓴 사람이 사라지므로 경고 이벤트는 내지 않는다.
		assertThat(events.stream(GuestbookEntryRestrictedEvent.class)).isEmpty();
	}

	@Test
	void 주인을_강제_탈퇴해도_신고와_글은_남고_처리할_수_있다() throws Exception {
		User admin = admin();
		long entry = writeOk(lee, kim, "신고된 글");
		report(kim, entry, "{}").andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/admin/users/" + kim.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		adminReports(admin, "open").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].ownerId").value(nullValue()))
			.andExpect(jsonPath("$[0].ownerName").value(nullValue()))
			.andExpect(jsonPath("$[0].ownerEmail").value(nullValue()))
			.andExpect(jsonPath("$[0].authorName").value("이영희"))
			.andExpect(jsonPath("$[0].body").value("신고된 글"));

		restrict(admin, reportIdOf(admin, entry)).andExpect(status().isNoContent());
		assertThat(events.stream(GuestbookEntryRestrictedEvent.class))
			.containsExactly(new GuestbookEntryRestrictedEvent(entry, lee.getId(), null));
		alerts(lee).andExpect(jsonPath("$.warnings", hasSize(1)))
			.andExpect(jsonPath("$.warnings[0].owner").value(nullValue()))
			.andExpect(jsonPath("$.warnings[0].body").value("신고된 글"));
		// 주인이 없는 글은 아무도 지우거나 신고할 수 없다.
		deleteEntry(park, entry).andExpect(status().isNotFound());
	}

	private ResultActions stats(User admin) throws Exception {
		return mockMvc.perform(get("/api/admin/stats").with(loginAs(admin)));
	}

	/** 관리자 신고 목록에서 그 글의 신고 한 칸 */
	private static List<Object> field(String reports, long entryId, String name) {
		return JsonPath.read(reports, "$[?(@.entryId == " + entryId + ")]." + name);
	}
}
