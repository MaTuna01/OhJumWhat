package com.ohjumwhat.menu;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollDetailResponse;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;
import com.ohjumwhat.vote.VoteService;

class MenuCommentIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	MenuService menuService;

	@Autowired
	MenuCommentService menuCommentService;

	@Autowired
	MenuCommentRepository menuCommentRepository;

	@Autowired
	VoteService voteService;

	User admin;

	User kim;

	User lee;

	User park;

	Long pollId;

	/** 김철수가 올린 김치찌개 */
	Long kimchiId;

	/** 김철수가 올린 돈가스 */
	Long donkatsuId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0); // 수요일 오전 11:00 (한국 시간), 투표는 11:50 마감
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		OrganizationResponse dev = organizationService.create(kim.getId(), "개발팀");
		inviteService.join(dev.inviteToken(), lee.getId());

		pollId = pollService.create(dev.id(), kim.getId(), new PollRequest("점심", "11:50")).id();
		menuService.add(pollId, kim.getId(), "김치찌개", null);
		PollDetailResponse detail = menuService.add(pollId, kim.getId(), "돈가스", null);
		kimchiId = detail.options().get(0).id();
		donkatsuId = detail.options().get(1).id();
	}

	@Test
	void 멤버는_진행_중인_투표의_메뉴에_댓글을_쓰고_댓글_수가_투표_상세에_보인다() throws Exception {
		addComment(kim, kimchiId, "  웨이팅 길어요  ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].body").value("웨이팅 길어요"))
			.andExpect(jsonPath("$[0].author.userId").value(kim.getId()))
			.andExpect(jsonPath("$[0].author.name").value("김철수"))
			.andExpect(jsonPath("$[0].createdAt").value("2026-09-30T02:00:00Z"))
			.andExpect(jsonPath("$[0].edited").value(false))
			.andExpect(jsonPath("$[0].mine").value(true));
		addComment(lee, kimchiId, "11시 반 전에 가면 괜찮아요").andExpect(status().isCreated());

		mockMvc.perform(get(commentsUrl(kimchiId)).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].mine").value(false))
			.andExpect(jsonPath("$[1].author.name").value("이영희"))
			.andExpect(jsonPath("$[1].mine").value(true));

		assertThat(menuCommentService.list(pollId, donkatsuId, kim.getId())).isEmpty();
		PollDetailResponse detail = pollService.detail(pollService.getForMember(pollId, kim.getId()), kim.getId());
		assertThat(detail.options()).extracting(PollDetailResponse.Option::commentCount).containsExactly(2L, 0L);
	}

	@Test
	void 멤버가_아니면_404이고_다른_투표의_메뉴나_다른_메뉴의_댓글은_찾을_수_없다() throws Exception {
		mockMvc.perform(get(commentsUrl(kimchiId)).with(loginAs(park)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("투표를 찾을 수 없어요."));
		addComment(park, kimchiId, "안녕하세요").andExpect(status().isNotFound());

		Long parkOrgId = organizationService.create(park.getId(), "디자인팀").id();
		Long parkPollId = pollService.create(parkOrgId, park.getId(), new PollRequest("점심", "11:50")).id();
		Long parkOptionId = menuService.add(parkPollId, park.getId(), "쌀국수", null).options().getFirst().id();
		mockMvc.perform(get(commentsUrl(parkOptionId)).with(loginAs(kim)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("메뉴를 찾을 수 없어요."));

		Long commentId = menuCommentService.add(pollId, kimchiId, kim.getId(), "맛있어요").getFirst().id();
		editComment(kim, donkatsuId, commentId, "고쳐요")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("댓글을 찾을 수 없어요."));
	}

	@Test
	void 내가_쓴_댓글만_고치고_지울_수_있다() throws Exception {
		Long commentId = menuCommentService.add(pollId, kimchiId, kim.getId(), "웨이팅 길어요").getFirst().id();

		editComment(lee, kimchiId, commentId, "제가 고칠게요")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("내가 쓴 댓글만 고칠 수 있어요."));
		deleteComment(lee, kimchiId, commentId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("내가 쓴 댓글만 지울 수 있어요."));

		clock.set(2026, 9, 30, 11, 10);
		editComment(kim, kimchiId, commentId, "웨이팅 없어요")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].body").value("웨이팅 없어요"))
			.andExpect(jsonPath("$[0].edited").value(true))
			.andExpect(jsonPath("$[0].createdAt").value("2026-09-30T02:00:00Z"));
		deleteComment(kim, kimchiId, commentId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 마감된_투표의_댓글은_읽기만_한다() throws Exception {
		Long commentId = menuCommentService.add(pollId, kimchiId, kim.getId(), "웨이팅 길어요").getFirst().id();

		clock.set(2026, 9, 30, 11, 49);
		editComment(kim, kimchiId, commentId, "웨이팅 없어요").andExpect(status().isOk());

		clock.set(2026, 9, 30, 11, 50);
		addComment(lee, kimchiId, "맛있었어요")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("마감된 투표예요."));
		editComment(kim, kimchiId, commentId, "다시 고쳐요").andExpect(status().isConflict());
		deleteComment(kim, kimchiId, commentId).andExpect(status().isConflict());
		mockMvc.perform(get(commentsUrl(kimchiId)).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].body").value("웨이팅 없어요"));
	}

	@Test
	void 댓글은_비울_수_없고_줄바꿈_같은_제어_문자를_막고_200자까지_쓴다() throws Exception {
		addComment(kim, kimchiId, "   ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));
		mockMvc.perform(post(commentsUrl(kimchiId)).with(loginAs(kim)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내용을 입력해 주세요."));
		addComment(kim, kimchiId, "첫 줄\\n둘째 줄")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("쓸 수 없는 문자가 있어요."));
		addComment(kim, kimchiId, "가".repeat(201))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("200자 이하로 입력해 주세요."));

		// 글자(코드 포인트) 수로 센다: 이모지 200개도 200자다.
		addComment(kim, kimchiId, "가".repeat(200)).andExpect(status().isCreated());
		addComment(kim, kimchiId, "😋".repeat(200)).andExpect(status().isCreated());
	}

	@Test
	void 메뉴를_지우면_댓글도_함께_지운다() throws Exception {
		menuCommentService.add(pollId, donkatsuId, lee.getId(), "어제 먹었어요");
		menuCommentService.add(pollId, kimchiId, lee.getId(), "좋아요");

		mockMvc.perform(delete("/api/polls/" + pollId + "/options/" + donkatsuId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options", hasSize(1)))
			.andExpect(jsonPath("$.options[0].commentCount").value(1));
		assertThat(menuCommentRepository.findAll()).extracting(MenuComment::getOptionId).containsExactly(kimchiId);
	}

	@Test
	void 강제_탈퇴한_회원의_댓글은_탈퇴한_사용자로_남는다() throws Exception {
		menuCommentService.add(pollId, kimchiId, lee.getId(), "웨이팅 길어요");

		mockMvc.perform(delete("/api/admin/users/" + lee.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get(commentsUrl(kimchiId)).with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].body").value("웨이팅 길어요"))
			.andExpect(jsonPath("$[0].author").value(nullValue()))
			.andExpect(jsonPath("$[0].mine").value(false));
	}

	@Test
	void 관리자는_마감과_상관없이_댓글을_보고_지운다() throws Exception {
		Long commentId = menuCommentService.add(pollId, kimchiId, lee.getId(), "부적절한 글").getFirst().id();
		menuCommentService.add(pollId, donkatsuId, kim.getId(), "남은 댓글");
		voteService.vote(pollId, kim.getId(), donkatsuId);
		clock.set(2026, 9, 30, 12, 0);

		mockMvc.perform(get("/api/admin/menu-options/" + kimchiId + "/comments").with(loginAs(kim)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/menu-options/" + kimchiId + "/comments").with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].body").value("부적절한 글"));
		mockMvc.perform(get("/api/admin/menu-options/0/comments").with(loginAs(admin)))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/admin/menu-comments/" + commentId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/menu-comments/" + commentId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(delete("/api/admin/menu-comments/" + commentId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("댓글을 찾을 수 없어요."));

		// 관리자가 참여자가 있는 메뉴를 지워도 댓글이 함께 지워진다.
		mockMvc.perform(delete("/api/admin/menu-options/" + donkatsuId).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isOk());
		assertThat(menuCommentRepository.findAll()).isEmpty();
	}

	private String commentsUrl(Long optionId) {
		return "/api/polls/" + pollId + "/options/" + optionId + "/comments";
	}

	private ResultActions addComment(User user, Long optionId, String body) throws Exception {
		return mockMvc.perform(post(commentsUrl(optionId)).with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"" + body + "\"}"));
	}

	private ResultActions editComment(User user, Long optionId, Long commentId, String body) throws Exception {
		return mockMvc.perform(put(commentsUrl(optionId) + "/" + commentId).with(loginAs(user)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"" + body + "\"}"));
	}

	private ResultActions deleteComment(User user, Long optionId, Long commentId) throws Exception {
		return mockMvc.perform(delete(commentsUrl(optionId) + "/" + commentId).with(loginAs(user)).with(xsrf()));
	}
}
