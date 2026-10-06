package com.ohjumwhat.guestbook;

import static com.ohjumwhat.TestAuth.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ohjumwhat.user.User;

/** 방명록 보기·쓰기·지우기와 응답 모양 */
@RecordApplicationEvents
class GuestbookIntegrationTest extends GuestbookTestBase {

	@Autowired
	ApplicationEvents events;

	@Test
	void 열두_개를_쓰면_최신순_열_개와_두_개로_나뉜다() throws Exception {
		for (int i = 1; i <= 12; i++) {
			writeOk(i % 2 == 0 ? lee : park, kim, "글 " + i);
		}

		list(lee, kim.getId(), 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.entries", hasSize(10)))
			.andExpect(jsonPath("$.entries[0].body").value("글 12"))
			.andExpect(jsonPath("$.entries[9].body").value("글 3"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.totalCount").value(12))
			.andExpect(jsonPath("$.owner").value(false))
			.andExpect(jsonPath("$.seenAt").value(nullValue()));
		list(lee, kim.getId(), 1).andExpect(jsonPath("$.entries", hasSize(2)))
			.andExpect(jsonPath("$.entries[0].body").value("글 2"))
			.andExpect(jsonPath("$.entries[1].body").value("글 1"))
			.andExpect(jsonPath("$.page").value(1));
		// 끝을 넘은 쪽은 비어 있고 개수는 실제 값이다.
		list(lee, kim.getId(), 5).andExpect(status().isOk())
			.andExpect(jsonPath("$.entries", hasSize(0)))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.totalCount").value(12));
		// page가 없으면 0쪽, 글이 없으면 쪽도 0개
		mockMvc.perform(get("/api/guestbook/users/" + park.getId()).with(loginAs(lee)))
			.andExpect(jsonPath("$.entries", hasSize(0)))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.totalPages").value(0))
			.andExpect(jsonPath("$.totalCount").value(0));
	}

	@Test
	void 쪽_번호가_음수면_400이다() throws Exception {
		list(kim, kim.getId(), -1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("페이지 번호가 올바르지 않아요."));
	}

	@Test
	void 쪽_번호가_너무_크면_500이_아니라_400이다() throws Exception {
		// offset(쪽 × 10)이 int를 넘으면 Spring Data가 500을 낸다.
		list(kim, kim.getId(), 300_000_000).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("페이지 번호가 올바르지 않아요."));
		list(kim, kim.getId(), GuestbookService.MAX_PAGE).andExpect(status().isOk());
	}

	@Test
	void 다른_조직_사람이_쓴_글도_주인과_같은_조직이면_보인다() throws Exception {
		// 최(디자인팀)가 김의 방명록에 쓰면, 김과 개발팀만 같이 쓰는 이에게도 보인다.
		writeOk(choi, kim, "디자인팀에서 왔어요");

		list(lee, kim).andExpect(status().isOk())
			.andExpect(jsonPath("$.entries", hasSize(1)))
			.andExpect(jsonPath("$.entries[0].author.userId").value(choi.getId()))
			.andExpect(jsonPath("$.entries[0].author.name").value("최준호"))
			.andExpect(jsonPath("$.entries[0].body").value("디자인팀에서 왔어요"))
			.andExpect(jsonPath("$.entries[0].createdAt").value("2026-10-06T03:00:00Z"));
	}

	@Test
	void 같은_조직이_아니면_방명록을_보지도_쓰지도_못한다() throws Exception {
		list(lee, choi).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("방명록을 찾을 수 없어요."));
		write(lee, choi, "안녕하세요").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("방명록을 찾을 수 없어요."));
		// 없는 회원도 같은 404다.
		list(lee, 9999L, 0).andExpect(status().isNotFound());
		write(lee, 9999L, "안녕하세요").andExpect(status().isNotFound());
		// 404가 난 시도는 도배 방지에 세지 않는다.
		write(lee, kim, "안녕하세요").andExpect(status().isCreated());
		// 같은 조직 확인이 도배 방지보다 먼저다(바로 다시 써도 볼 수 없는 방명록이면 404).
		write(lee, choi, "또 왔어요").andExpect(status().isNotFound());
	}

	@Test
	void 내_방명록에는_쓸_수_없고_조직이_없어도_내_방명록은_본다() throws Exception {
		write(kim, kim, "혼잣말").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내 방명록에는 글을 남길 수 없어요."));
		// 글 확인이 먼저다.
		write(kim, kim, "   ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));

		User solo = stranger();
		list(solo, solo).andExpect(status().isOk())
			.andExpect(jsonPath("$.entries", hasSize(0)))
			.andExpect(jsonPath("$.owner").value(true))
			.andExpect(jsonPath("$.seenAt").value(nullValue()));
	}

	@Test
	void 글은_한_줄_100자까지이고_이모지는_한_글자로_센다() throws Exception {
		write(lee, kim, "가".repeat(101)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("100자 이하로 입력해 주세요."));
		write(lee, kim, "첫 줄\\n둘째 줄").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("쓸 수 없는 문자가 있어요."));
		write(lee, kim, "   ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));
		// 잘못 쓴 글은 도배 방지에 세지 않아 바로 쓸 수 있다.
		write(lee, kim, "😀" + "가".repeat(99)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.entries[0].body").value("😀" + "가".repeat(99)));
		later(5);
		// 앞뒤 공백은 지운다.
		write(lee, kim, "  반가워요  ").andExpect(status().isCreated())
			.andExpect(jsonPath("$.entries[0].body").value("반가워요"));
	}

	@Test
	void 한_사람은_모든_방명록을_합쳐_5초에_한_번_쓴다() throws Exception {
		write(lee, kim, "하나").andExpect(status().isCreated());

		later(4);
		write(lee, kim, "둘").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.message").value("방명록은 5초에 한 번 남길 수 있어요. 잠시 후 다시 남겨 주세요."));
		// 다른 사람의 방명록도 같이 센다.
		write(lee, park, "둘").andExpect(status().isTooManyRequests());
		// 다른 사람은 따로 센다.
		write(park, kim, "박이에요").andExpect(status().isCreated());

		later(1);
		write(lee, kim, "둘").andExpect(status().isCreated());
	}

	@Test
	void 쓰면_쓴_사람이_보는_첫_쪽을_돌려준다() throws Exception {
		writeOk(park, kim, "박의 글");

		write(lee, kim, "이의 글").andExpect(status().isCreated())
			.andExpect(jsonPath("$.entries", hasSize(2)))
			.andExpect(jsonPath("$.entries[0].body").value("이의 글"))
			.andExpect(jsonPath("$.entries[0].mine").value(true))
			.andExpect(jsonPath("$.entries[0].canDelete").value(true))
			.andExpect(jsonPath("$.entries[1].mine").value(false))
			.andExpect(jsonPath("$.entries[1].canDelete").value(false))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.totalCount").value(2))
			.andExpect(jsonPath("$.owner").value(false))
			.andExpect(jsonPath("$.seenAt").value(nullValue()));
	}

	@Test
	void 쓴_사람과_주인만_지우고_남은_볼_수_있으면_403_아니면_404다() throws Exception {
		long leeEntry = writeOk(lee, kim, "이의 글");
		long parkEntry = writeOk(park, kim, "박의 글");
		User stranger = stranger();

		// 박(같은 조직)·최(디자인팀으로 김과 같은 조직)는 그 방명록을 볼 수 있어 403이다.
		deleteEntry(park, leeEntry).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("내가 쓴 글이나 내 방명록의 글만 지울 수 있어요."));
		deleteEntry(choi, leeEntry).andExpect(status().isForbidden());
		deleteEntry(stranger, leeEntry).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("방명록 글을 찾을 수 없어요."));

		deleteEntry(lee, leeEntry).andExpect(status().isNoContent());
		deleteEntry(lee, leeEntry).andExpect(status().isNotFound());
		deleteEntry(kim, parkEntry).andExpect(status().isNoContent());
		deleteEntry(kim, 9999L).andExpect(status().isNotFound());

		list(kim, kim).andExpect(jsonPath("$.entries", hasSize(0))).andExpect(jsonPath("$.totalCount").value(0));
	}

	@Test
	void 조직을_떠난_쓴_사람도_자기_글을_지운다() throws Exception {
		long entry = writeOk(choi, kim, "디자인팀에서 왔어요");
		organizationService.leave(designId, choi.getId());

		list(choi, kim).andExpect(status().isNotFound());
		deleteEntry(choi, entry).andExpect(status().isNoContent());
		list(lee, kim).andExpect(jsonPath("$.entries", hasSize(0)));
	}

	@Test
	void 주인에게만_신고_여부와_본_시각을_준다() throws Exception {
		long leeEntry = writeOk(lee, kim, "이의 글");
		writeOk(park, kim, "박의 글");
		report(kim, leeEntry, "{}").andExpect(status().isNoContent());
		seen(kim, clock.instant()).andExpect(status().isNoContent());

		list(kim, kim).andExpect(jsonPath("$.owner").value(true))
			.andExpect(jsonPath("$.seenAt").value("2026-10-06T03:00:10Z"))
			.andExpect(jsonPath("$.entries[0].body").value("박의 글"))
			.andExpect(jsonPath("$.entries[0].mine").value(false))
			.andExpect(jsonPath("$.entries[0].canDelete").value(true))
			.andExpect(jsonPath("$.entries[0].canReport").value(true))
			.andExpect(jsonPath("$.entries[0].reported").value(false))
			.andExpect(jsonPath("$.entries[0].restricted").value(false))
			.andExpect(jsonPath("$.entries[1].canReport").value(false))
			.andExpect(jsonPath("$.entries[1].reported").value(true));
		// 쓴 사람에게도 신고했는지는 알리지 않는다.
		list(lee, kim).andExpect(jsonPath("$.owner").value(false))
			.andExpect(jsonPath("$.seenAt").value(nullValue()))
			.andExpect(jsonPath("$.entries[1].body").value("이의 글"))
			.andExpect(jsonPath("$.entries[1].mine").value(true))
			.andExpect(jsonPath("$.entries[1].canDelete").value(true))
			.andExpect(jsonPath("$.entries[1].canReport").value(false))
			.andExpect(jsonPath("$.entries[1].reported").value(false))
			.andExpect(jsonPath("$.entries[0].mine").value(false))
			.andExpect(jsonPath("$.entries[0].canDelete").value(false))
			.andExpect(jsonPath("$.entries[0].canReport").value(false));
	}

	@Test
	void 쓴_사람은_별명과_올린_사진_없으면_구글_사진으로_보인다() throws Exception {
		jdbcTemplate.update("update users set nickname = '점심요정', photo_key = 'leephotokey00001' where id = ?",
				lee.getId());
		jdbcTemplate.update("update users set profile_image_url = ? where id = ?",
				"https://lh3.googleusercontent.com/park", park.getId());
		writeOk(lee, kim, "이의 글");
		writeOk(park, kim, "박의 글");

		list(choi, kim).andExpect(jsonPath("$.entries[1].author.name").value("점심요정"))
			.andExpect(jsonPath("$.entries[1].author.profileImageUrl").value("/api/photos/leephotokey00001.jpg"))
			.andExpect(jsonPath("$.entries[0].author.name").value("박민수"))
			.andExpect(jsonPath("$.entries[0].author.profileImageUrl")
				.value("https://lh3.googleusercontent.com/park"));
	}

	@Test
	void 쓰면_새_글_이벤트를_내고_실패하면_내지_않는다() throws Exception {
		long entry = writeOk(lee, kim, "이의 글");

		assertThat(events.stream(GuestbookEntryCreatedEvent.class))
			.containsExactly(new GuestbookEntryCreatedEvent(entry, kim.getId(), lee.getId()));

		write(kim, kim, "혼잣말").andExpect(status().isBadRequest());
		write(lee, choi, "안녕").andExpect(status().isNotFound());
		write(lee, kim, "").andExpect(status().isBadRequest());
		write(park, kim, "하나").andExpect(status().isCreated());
		write(park, kim, "둘").andExpect(status().isTooManyRequests());
		assertThat(events.stream(GuestbookEntryCreatedEvent.class)).hasSize(2);
	}
}
