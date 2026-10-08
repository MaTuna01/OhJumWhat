package com.ohjumwhat.sanction;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.FakeKakaoLocalConfiguration;
import com.ohjumwhat.FakeKakaoLocalConfiguration.FakeKakaoLocal;
import com.ohjumwhat.FakeNaverShortLinksConfiguration;
import com.ohjumwhat.FakeNaverShortLinksConfiguration.FakeNaverShortLinks;
import com.ohjumwhat.TestImages;
import com.ohjumwhat.chat.ChatService;
import com.ohjumwhat.guestbook.GuestbookService;
import com.ohjumwhat.letter.LetterService;
import com.ohjumwhat.menu.MenuCommentService;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.schedule.ScheduleRequest;
import com.ohjumwhat.schedule.ScheduleService;
import com.ohjumwhat.user.User;

/**
 * 제재로 막는 API: 자기 종류·활동 정지면 423(컨트롤러보다 먼저), 다른 종류면 그대로 된다. 투표 참여·지우기·읽음 등은 활동
 * 정지여도 된다. 기간이 끝나거나 해제되면 풀리고, 겹치면 늦게 끝나는 쪽(무기한 우선)으로 안내한다.
 */
class SanctionRestrictionIntegrationTest extends SanctionTestBase {

	/** 423 문구(계약서 그대로). %s는 끝나는 시각 */
	static final Map<Restriction, String> MESSAGES = Map.of(
			Restriction.SUSPEND, "활동이 정지된 상태예요(%s). 투표 참여만 할 수 있어요.",
			Restriction.POLL, "관리자가 메뉴 올리기·댓글·투표 관리를 제한했어요(%s). 투표 참여는 할 수 있어요.",
			Restriction.CHAT, "관리자가 채팅을 제한했어요(%s).",
			Restriction.LETTER, "관리자가 쪽지 보내기를 제한했어요(%s).",
			Restriction.GUESTBOOK, "관리자가 방명록 쓰기를 제한했어요(%s).",
			Restriction.PROFILE, "관리자가 프로필 수정을 제한했어요(%s).");

	@Autowired
	PollService pollService;

	@Autowired
	MenuService menuService;

	@Autowired
	MenuCommentService menuCommentService;

	@Autowired
	ChatService chatService;

	@Autowired
	LetterService letterService;

	@Autowired
	ScheduleService scheduleService;

	@Autowired
	GuestbookService guestbookService;

	@Autowired
	FakeKakaoLocal fakeKakaoLocal;

	@Autowired
	FakeNaverShortLinks fakeNaverShortLinks;

	/** 이영희 혼자 있는 조직의 초대 링크 */
	String designInviteToken;

	/** 김철수가 연 수동 투표(11:50 마감) */
	Long pollId;

	/** 김철수가 올린 메뉴(참여자 없음)와 이영희가 올린 메뉴 */
	Long kimOptionId;

	Long leeOptionId;

	/** 김철수가 쓴 댓글·채팅·정기 투표 규칙·방명록 글, 이영희가 김철수에게 보낸 쪽지 */
	Long commentId;

	Long messageId;

	Long scheduleId;

	Long guestbookEntryId;

	Long letterToKimId;

	@BeforeEach
	void setUp() {
		designInviteToken = organizationService.create(lee.getId(), "디자인팀").inviteToken();
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
		kimOptionId = menuService.add(pollId, kim.getId(), "김치찌개", null).options().get(0).id();
		PollDetailResponse detail = menuService.add(pollId, lee.getId(), "마라탕", null);
		leeOptionId = detail.options().get(1).id();
		commentId = menuCommentService.add(pollId, kimOptionId, kim.getId(), "맛있어요").get(0).id();
		messageId = chatService.send(pollId, kim.getId(), "점심 뭐 먹어요?").id();
		scheduleId = scheduleService.create(orgId, kim.getId(), new ScheduleRequest("평일 점심", 31, "10:00", "11:30"))
			.id();
		guestbookEntryId = guestbookService.write(kim.getId(), lee.getId(), "안녕하세요").entries().get(0).id();
		letterToKimId = letterService.send(lee.getId(), orgId, kim.getId(), "점심 같이 먹어요", false).id();
		clock.set(2026, 10, 7, 11, 5); // 투표가 열린 지 5분 뒤(지금 마감할 수 있다), 방명록 도배 방지 시간도 지났다.
	}

	/** 막는 API마다: 요청과 막히지 않았을 때의 응답 코드 */
	static Stream<Endpoint> restrictedEndpoints() {
		return Stream.of(
				// 활동 정지만 막는다
				new Endpoint("조직 만들기", Restriction.SUSPEND, HttpStatus.CREATED,
						t -> json(post("/api/orgs"), "{\"name\": \"새 조직\"}")),
				new Endpoint("조직 이름 바꾸기", Restriction.SUSPEND, HttpStatus.OK,
						t -> json(patch("/api/orgs/{id}", t.orgId), "{\"name\": \"개발 1팀\"}")),
				new Endpoint("조직 위치 바꾸기", Restriction.SUSPEND, HttpStatus.OK,
						t -> json(put("/api/orgs/{id}/location", t.orgId), "{\"area\": \"역삼동\"}")),
				new Endpoint("초대로 참여", Restriction.SUSPEND, HttpStatus.OK,
						t -> post("/api/invites/{token}/join", t.designInviteToken)),
				// 투표 제한
				new Endpoint("투표 만들기", Restriction.POLL, HttpStatus.CREATED,
						t -> json(post("/api/orgs/{id}/polls", t.orgId), "{\"title\": \"저녁\", \"closesAt\": \"18:00\"}")),
				new Endpoint("투표 수정", Restriction.POLL, HttpStatus.OK,
						t -> json(put("/api/polls/{id}", t.pollId), "{\"title\": \"점심!\", \"closesAt\": \"11:55\"}")),
				new Endpoint("지금 마감", Restriction.POLL, HttpStatus.OK, t -> post("/api/polls/{id}/close", t.pollId)),
				new Endpoint("투표 삭제", Restriction.POLL, HttpStatus.NO_CONTENT, t -> delete("/api/polls/{id}", t.pollId)),
				new Endpoint("메뉴 추가", Restriction.POLL, HttpStatus.CREATED,
						t -> json(post("/api/polls/{id}/options", t.pollId), "{\"name\": \"돈가스\"}")),
				new Endpoint("식당 붙이기", Restriction.POLL, HttpStatus.OK,
						t -> json(put("/api/polls/{id}/options/{optionId}/link", t.pollId, t.kimOptionId),
								"{\"link\": \"https://map.naver.com/p/entry/place/1868364770\", \"placeName\": \"할매집\"}")),
				new Endpoint("정기 투표 만들기", Restriction.POLL, HttpStatus.CREATED,
						t -> json(post("/api/orgs/{id}/schedules", t.orgId), """
								{"name": "저녁 투표", "daysOfWeek": 31, "openTime": "16:00", "closeTime": "18:00"}""")),
				new Endpoint("정기 투표 수정", Restriction.POLL, HttpStatus.OK,
						t -> json(put("/api/orgs/{id}/schedules/{scheduleId}", t.orgId, t.scheduleId), """
								{"name": "평일 점심", "daysOfWeek": 15, "openTime": "10:00", "closeTime": "11:30"}""")),
				new Endpoint("정기 투표 삭제", Restriction.POLL, HttpStatus.NO_CONTENT,
						t -> delete("/api/orgs/{id}/schedules/{scheduleId}", t.orgId, t.scheduleId)),
				new Endpoint("메뉴 댓글 쓰기", Restriction.POLL, HttpStatus.CREATED,
						t -> json(post("/api/polls/{id}/options/{optionId}/comments", t.pollId, t.kimOptionId),
								"{\"body\": \"좋아요\"}")),
				new Endpoint("메뉴 댓글 고치기", Restriction.POLL, HttpStatus.OK,
						t -> json(put("/api/polls/{id}/options/{optionId}/comments/{commentId}", t.pollId, t.kimOptionId,
								t.commentId), "{\"body\": \"고쳤어요\"}")),
				// 채팅 금지
				new Endpoint("채팅 보내기", Restriction.CHAT, HttpStatus.CREATED,
						t -> json(post("/api/polls/{id}/messages", t.pollId), "{\"body\": \"배고파요\"}")),
				new Endpoint("채팅 고치기", Restriction.CHAT, HttpStatus.OK,
						t -> json(put("/api/polls/{id}/messages/{messageId}", t.pollId, t.messageId),
								"{\"body\": \"고쳤어요\"}")),
				new Endpoint("채팅 사진 보내기", Restriction.CHAT, HttpStatus.CREATED,
						t -> multipart("/api/polls/{id}/messages/photo", t.pollId).file(photo())),
				// 쪽지 금지
				new Endpoint("쪽지 보내기", Restriction.LETTER, HttpStatus.CREATED,
						t -> json(post("/api/letters"), "{\"organizationId\": %d, \"recipientId\": %d, \"body\": \"안녕하세요\"}"
							.formatted(t.orgId, t.lee.getId()))),
				new Endpoint("쪽지 답장", Restriction.LETTER, HttpStatus.CREATED,
						t -> json(post("/api/letters/{id}/reply", t.letterToKimId), "{\"body\": \"좋아요\"}")),
				// 방명록 쓰기 금지
				new Endpoint("방명록 쓰기", Restriction.GUESTBOOK, HttpStatus.CREATED,
						t -> json(post("/api/guestbook/users/{id}", t.lee.getId()), "{\"body\": \"반가워요\"}")),
				// 프로필 수정 잠금
				new Endpoint("별명", Restriction.PROFILE, HttpStatus.OK,
						t -> json(put("/api/me/nickname"), "{\"nickname\": \"철수\"}")),
				new Endpoint("한줄 소개", Restriction.PROFILE, HttpStatus.OK,
						t -> json(put("/api/me/profile"), "{\"bio\": \"점심 좋아요\", \"foodTags\": [\"라멘\"]}")),
				new Endpoint("상세 프로필", Restriction.PROFILE, HttpStatus.OK, t -> json(put("/api/me/profile/details"), """
						{"mbti": "ENFP", "personalColor": "AUTUMN_WARM", "hobbies": ["등산"], "age": 32, "jobTitle": "사원"}""")),
				new Endpoint("사진 올리기", Restriction.PROFILE, HttpStatus.OK,
						t -> multipart("/api/me/photo").file(photo())),
				new Endpoint("사진 되돌리기", Restriction.PROFILE, HttpStatus.OK, t -> delete("/api/me/photo")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("restrictedEndpoints")
	void 자기_종류와_활동_정지면_423이고_다른_종류면_된다(Endpoint endpoint) throws Exception {
		long own = restrict(kim, 7, endpoint.type());
		perform(endpoint).andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value(message(endpoint.type(), "10월 14일 오전 11:05까지")));
		lift(own).andExpect(status().isOk());

		long suspend = restrict(kim, null, Restriction.SUSPEND);
		perform(endpoint).andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value(message(Restriction.SUSPEND, "해제될 때까지")));
		lift(suspend).andExpect(status().isOk());

		Restriction[] others = Arrays.stream(Restriction.values())
			.filter(r -> r != endpoint.type() && r != Restriction.SUSPEND)
			.toArray(Restriction[]::new);
		restrict(kim, null, others);
		perform(endpoint).andExpect(status().is(endpoint.success().value()));
	}

	@Test
	void 활동_정지여도_투표_참여_취소와_지우기_읽음_신고_탈퇴는_된다() throws Exception {
		Long secondLetterId = letterService.send(lee.getId(), orgId, kim.getId(), "저녁도 같이 먹어요", false).id();
		long suspend = restrict(kim, null, Restriction.SUSPEND);
		String session = loginSession(kim);

		expect(json(put("/api/polls/{id}/vote", pollId), "{\"optionId\": " + leeOptionId + "}"), HttpStatus.OK);
		expect(json(put("/api/polls/{id}/vote", pollId), "{\"optionId\": null}"), HttpStatus.OK);
		expect(delete("/api/polls/{id}/vote", pollId), HttpStatus.OK);
		expect(delete("/api/polls/{id}/options/{optionId}/comments/{commentId}", pollId, kimOptionId, commentId),
				HttpStatus.OK);
		expect(delete("/api/polls/{id}/options/{optionId}", pollId, kimOptionId), HttpStatus.OK);
		expect(json(put("/api/polls/{id}/messages/read", pollId), "{\"lastReadId\": " + messageId + "}"),
				HttpStatus.NO_CONTENT);
		expect(delete("/api/polls/{id}/messages/{messageId}", pollId, messageId), HttpStatus.OK);
		expect(put("/api/letters/{id}/read", letterToKimId), HttpStatus.NO_CONTENT);
		expect(delete("/api/letters/{id}", secondLetterId), HttpStatus.NO_CONTENT);
		expect(json(post("/api/letters/{id}/report", letterToKimId), "{\"reason\": \"스팸\", \"block\": true}"),
				HttpStatus.NO_CONTENT);
		expect(post("/api/letters/{id}/block", letterToKimId), HttpStatus.NO_CONTENT);
		String blocks = mockMvc.perform(get("/api/letters/blocks").with(loginAs(kim)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		expect(delete("/api/letters/blocks/{id}", ((Number) JsonPath.read(blocks, "$[0].id")).longValue()),
				HttpStatus.NO_CONTENT);
		expect(delete("/api/guestbook/entries/{id}", guestbookEntryId), HttpStatus.NO_CONTENT);
		expect(json(post("/api/guestbook/seen"), "{\"until\": \"2026-10-07T02:00:00Z\"}"), HttpStatus.NO_CONTENT);
		expect(json(post("/api/guestbook/warnings/ack"), "{\"until\": \"2026-10-07T02:00:00Z\"}"), HttpStatus.NO_CONTENT);
		expect(post("/api/notices/seen"), HttpStatus.NO_CONTENT);
		expect(json(post("/api/sanctions/seen"), "{\"ids\": [" + suspend + "]}"), HttpStatus.NO_CONTENT);
		mockMvc.perform(put("/api/push/devices/{fid}", "fid_kim_phone_000001").with(loginAs(kim)).with(xsrf(session)))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/push/devices/{fid}", "fid_kim_phone_000001").with(loginAs(kim)).with(xsrf(session)))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/orgs/{id}/polls/{pollId}", orgId, pollId).with(loginAs(kim)))
			.andExpect(status().isOk());
		expect(delete("/api/orgs/{id}/membership", orgId), HttpStatus.OK);
	}

	@Test
	void 기간이_끝나면_스스로_풀린다() throws Exception {
		clock.set(2026, 10, 7, 11, 0);
		restrict(kim, 1, Restriction.PROFILE);

		clock.set(2026, 10, 8, 10, 59); // 끝나기 1분 전
		nickname("철수").andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value("관리자가 프로필 수정을 제한했어요(10월 8일 오전 11:00까지)."));
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.sanctions", hasSize(1)));

		clock.set(2026, 10, 8, 11, 0);
		nickname("철수").andExpect(status().isOk());
		mockMvc.perform(get("/api/me").with(loginAs(kim))).andExpect(jsonPath("$.sanctions", empty()));
	}

	@Test
	void 해제하면_바로_풀리고_함께_한_초기화는_그대로다() throws Exception {
		nickname("철수").andExpect(status().isOk());
		long sanctionId = sanction(kim,
				"{\"restrictions\": [\"PROFILE\"], \"resets\": [\"NICKNAME\"], \"reason\": \"PROFILE\"}");
		nickname("다시 철수").andExpect(status().isLocked());

		lift(sanctionId).andExpect(status().isOk());
		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.nickname").value(nullValue()))
			.andExpect(jsonPath("$.name").value("김철수"))
			.andExpect(jsonPath("$.sanctions", empty()));
		nickname("다시 철수").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("다시 철수"));
	}

	@Test
	void 겹치면_가장_늦게_끝나는_시각이고_무기한이_있으면_해제될_때까지다() throws Exception {
		restrict(kim, 1, Restriction.CHAT);
		restrict(kim, 7, Restriction.CHAT, Restriction.LETTER);
		sendChat("배고파요").andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value("관리자가 채팅을 제한했어요(10월 14일 오전 11:05까지)."));

		restrict(kim, null, Restriction.CHAT);
		sendChat("배고파요").andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value("관리자가 채팅을 제한했어요(해제될 때까지)."));
		// 쪽지는 7일 제한만 걸려 있다.
		perform(json(post("/api/letters/{id}/reply", letterToKimId), "{\"body\": \"좋아요\"}"))
			.andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value("관리자가 쪽지 보내기를 제한했어요(10월 14일 오전 11:05까지)."));
	}

	@Test
	void 활동_정지와_겹치면_활동_정지_문구로_그_기능을_막는_제재들_중_가장_늦은_시각이다() throws Exception {
		restrict(kim, 1, Restriction.SUSPEND);
		restrict(kim, 7, Restriction.CHAT);

		sendChat("배고파요").andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value("활동이 정지된 상태예요(10월 14일 오전 11:05까지). 투표 참여만 할 수 있어요."));
		perform(json(post("/api/guestbook/users/{id}", lee.getId()), "{\"body\": \"반가워요\"}"))
			.andExpect(status().isLocked())
			.andExpect(jsonPath("$.message").value("활동이 정지된 상태예요(10월 8일 오전 11:05까지). 투표 참여만 할 수 있어요."));
	}

	@Test
	void 막히면_멤버_확인과_요청_값_검사보다_423이_먼저다() throws Exception {
		User park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		sendChat(park, "안녕하세요").andExpect(status().isNotFound());
		restrict(park, 7, Restriction.CHAT);
		sendChat(park, "안녕하세요").andExpect(status().isLocked());
		perform(park, json(post("/api/polls/{id}/messages", 9999), "{\"body\": \"없는 투표\"}"))
			.andExpect(status().isLocked());

		restrict(kim, 7, Restriction.CHAT, Restriction.GUESTBOOK, Restriction.POLL);
		sendChat("가".repeat(2001)).andExpect(status().isLocked());
		perform(json(post("/api/polls/{id}/messages", pollId), "{\"body\": ")).andExpect(status().isLocked());
		// 내 방명록에는 쓸 수 없다(400) → 423
		perform(json(post("/api/guestbook/users/{id}", kim.getId()), "{\"body\": \"자기 글\"}"))
			.andExpect(status().isLocked());
		perform(json(post("/api/orgs/{id}/polls", orgId), "{\"title\": \"\", \"closesAt\": \"25:00\"}"))
			.andExpect(status().isLocked());
	}

	@Test
	void 막힌_요청은_속도_제한을_쓰지_않는다() throws Exception {
		long chat = restrict(kim, 7, Restriction.CHAT);
		for (int i = 1; i <= 10; i++) {
			sendChat("메시지 " + i).andExpect(status().isLocked());
		}
		lift(chat).andExpect(status().isOk());

		// 막혔던 10번은 세지 않았다. setUp에서 보낸 한 개(11:00)는 10초가 지나 세지 않는다.
		for (int i = 1; i <= 10; i++) {
			sendChat("메시지 " + i).andExpect(status().isCreated());
		}
		sendChat("하나 더").andExpect(status().isTooManyRequests());
	}

	@Test
	void 막힌_요청은_바깥에_묻거나_사진_파일을_쓰지_않는다() throws Exception {
		restrict(kim, null, Restriction.SUSPEND);
		fakeKakaoLocal.reset();
		perform(json(put("/api/orgs/{id}/location", orgId),
				"{\"officeAddress\": \"" + FakeKakaoLocalConfiguration.OFFICE_ADDRESS + "\"}"))
			.andExpect(status().isLocked());
		assertThat(fakeKakaoLocal.calls()).isZero();

		int naverCalls = fakeNaverShortLinks.calls();
		perform(json(post("/api/polls/{id}/options", pollId),
				"{\"name\": \"칼국수\", \"link\": \"https://naver.me/" + FakeNaverShortLinksConfiguration.PLACE_CODE + "\"}"))
			.andExpect(status().isLocked());
		perform(json(put("/api/polls/{id}/options/{optionId}/link", pollId, kimOptionId),
				"{\"link\": \"https://naver.me/" + FakeNaverShortLinksConfiguration.PLACE_CODE + "\"}"))
			.andExpect(status().isLocked());
		assertThat(fakeNaverShortLinks.calls()).isEqualTo(naverCalls);

		perform(multipart("/api/me/photo").file(photo())).andExpect(status().isLocked());
		assertThat(photoFiles()).isEmpty();
	}

	@Test
	void 지금_걸려_있는_제재는_내_정보에_오래된_순으로_있고_끝났거나_해제된_것과_경고는_없다() throws Exception {
		clock.set(2026, 10, 7, 9, 0);
		restrict(kim, 1, Restriction.LETTER); // 내일 9:00에 끝난다
		long lifted = restrict(kim, 7, Restriction.GUESTBOOK);
		clock.set(2026, 10, 7, 10, 0);
		long chat = sanction(kim, "{\"restrictions\": [\"CHAT\"], \"days\": 3, \"reason\": \"SPAM\", \"note\": \"도배\"}");
		sanction(kim, "{\"reason\": \"ETC\"}");
		sanction(kim, "{\"resets\": [\"INTRO\"], \"reason\": \"PROFILE\"}");
		long forever = restrict(kim, null, Restriction.SUSPEND);
		restrict(lee, 7, Restriction.POLL);
		lift(lifted).andExpect(status().isOk());

		clock.set(2026, 10, 8, 9, 0); // 쪽지 제한이 끝났다
		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.sanctions", hasSize(2)))
			.andExpect(jsonPath("$.sanctions[0].id").value(chat))
			.andExpect(jsonPath("$.sanctions[0].restrictions[0]").value("CHAT"))
			.andExpect(jsonPath("$.sanctions[0].endsAt").value("2026-10-10T01:00:00Z"))
			.andExpect(jsonPath("$.sanctions[0].reason").value("SPAM"))
			.andExpect(jsonPath("$.sanctions[0].note").value("도배"))
			.andExpect(jsonPath("$.sanctions[0].createdAt").value("2026-10-07T01:00:00Z"))
			.andExpect(jsonPath("$.sanctions[1].id").value(forever))
			.andExpect(jsonPath("$.sanctions[1].restrictions[0]").value("SUSPEND"))
			.andExpect(jsonPath("$.sanctions[1].endsAt").value(nullValue()))
			.andExpect(jsonPath("$.sanctions[1].note").value(nullValue()));
		mockMvc.perform(get("/api/me").with(loginAs(lee))).andExpect(jsonPath("$.sanctions", hasSize(1)));
		// 프로필을 바꾼 응답에도 함께 간다.
		mockMvc.perform(put("/api/me/nickname").with(loginAs(lee)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\": \"영희\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sanctions[0].restrictions[0]").value("POLL"));
	}

	private static String message(Restriction type, String until) {
		return MESSAGES.get(type).formatted(until);
	}

	private ResultActions perform(Endpoint endpoint) throws Exception {
		return perform(endpoint.request().build(this));
	}

	private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return perform(kim, request);
	}

	private ResultActions perform(User user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.with(loginAs(user)).with(xsrf()));
	}

	private void expect(AbstractMockHttpServletRequestBuilder<?> request, HttpStatus expected) throws Exception {
		perform(request).andExpect(status().is(expected.value()));
	}

	private ResultActions nickname(String nickname) throws Exception {
		return perform(json(put("/api/me/nickname"), "{\"nickname\": \"" + nickname + "\"}"));
	}

	private ResultActions sendChat(String body) throws Exception {
		return sendChat(kim, body);
	}

	private ResultActions sendChat(User user, String body) throws Exception {
		return perform(user, json(post("/api/polls/{id}/messages", pollId), "{\"body\": \"" + body + "\"}"));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockMultipartFile photo() {
		return new MockMultipartFile("photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg(300, 300));
	}

	/** 그 API의 요청(테스트의 setUp 값으로 만든다) */
	interface Request {

		AbstractMockHttpServletRequestBuilder<?> build(SanctionRestrictionIntegrationTest test) throws Exception;
	}

	/**
	 * @param name 테스트 이름에 보일 API
	 * @param type 막는 제한
	 * @param success 막히지 않았을 때의 응답 코드
	 */
	record Endpoint(String name, Restriction type, HttpStatus success, Request request) {

		@Override
		public String toString() {
			return name + " (" + type + ")";
		}
	}
}
