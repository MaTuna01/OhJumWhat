package com.ohjumwhat.user;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.TestImages;
import com.ohjumwhat.menu.MenuCommentService;
import com.ohjumwhat.menu.MenuService;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.vote.VoteService;

class ProfilePhotoIntegrationTest extends IntegrationTest {

	private static final String KIM_GOOGLE_PHOTO = "https://lh3.googleusercontent.com/kim";

	private static final String LEE_GOOGLE_PHOTO = "https://lh3.googleusercontent.com/lee";

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	MenuService menuService;

	@Autowired
	VoteService voteService;

	@Autowired
	MenuCommentService menuCommentService;

	@Autowired
	TransactionTemplate transactionTemplate;

	User kim;

	User lee;

	User admin;

	Long orgId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", KIM_GOOGLE_PHOTO));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", LEE_GOOGLE_PHOTO));
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		var org = organizationService.create(kim.getId(), "개발팀");
		orgId = org.id();
		inviteService.join(org.inviteToken(), lee.getId());
	}

	@Test
	void 사진을_올리면_내_정보와_멤버_목록과_투표_명단과_댓글과_관리자_화면에_올린_사진이_보인다() throws Exception {
		String url = photoUrl(upload(kim, TestImages.jpeg(512, 512))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileImageUrl", startsWith("/api/photos/")))
			.andExpect(jsonPath("$.googleProfileImageUrl").value(KIM_GOOGLE_PHOTO))
			.andExpect(jsonPath("$.customPhoto").value(true)));

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.profileImageUrl").value(url));
		mockMvc.perform(get("/api/orgs/" + orgId + "/members").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].profileImageUrl").value(url))
			.andExpect(jsonPath("$[1].profileImageUrl").value(LEE_GOOGLE_PHOTO));

		Long pollId = pollService.create(orgId, lee.getId(), new PollRequest("점심", "11:50")).id();
		Long optionId = menuService.add(pollId, kim.getId(), "김치찌개", null).options().getFirst().id();
		voteService.vote(pollId, kim.getId(), optionId);
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/" + pollId).with(loginAs(lee)))
			.andExpect(jsonPath("$.options[0].createdBy.profileImageUrl").value(url))
			.andExpect(jsonPath("$.options[0].voters[0].profileImageUrl").value(url))
			.andExpect(jsonPath("$.nonRespondents[0].profileImageUrl").value(LEE_GOOGLE_PHOTO));
		menuCommentService.add(pollId, optionId, kim.getId(), "여기 맛있어요");
		menuCommentService.add(pollId, optionId, lee.getId(), "좋아요");
		mockMvc.perform(get("/api/polls/" + pollId + "/options/" + optionId + "/comments").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].author.profileImageUrl").value(url))
			.andExpect(jsonPath("$[1].author.profileImageUrl").value(LEE_GOOGLE_PHOTO));

		mockMvc.perform(get("/api/admin/users/" + kim.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.profileImageUrl").value(url))
			.andExpect(jsonPath("$.user.customPhoto").value(true));
		mockMvc.perform(get("/api/admin/users").param("q", "이영희").with(loginAs(admin)))
			.andExpect(jsonPath("$[0].profileImageUrl").value(LEE_GOOGLE_PHOTO))
			.andExpect(jsonPath("$[0].customPhoto").value(false));
		mockMvc.perform(get("/api/admin/orgs/" + orgId).with(loginAs(admin)))
			.andExpect(jsonPath("$.members[0].profileImageUrl").value(url));
	}

	@Test
	void 올린_사진은_로그인한_사람에게_256px_JPEG로_오래_캐시되게_보낸다() throws Exception {
		String url = photoUrl(upload(kim, TestImages.transparentPng(800, 600)));

		byte[] body = mockMvc.perform(get(url).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(header().string("Cache-Control", containsString("immutable")))
			.andExpect(header().string("Cache-Control", containsString("private")))
			.andExpect(header().doesNotExist("Pragma"))
			.andReturn().getResponse().getContentAsByteArray();
		BufferedImage image = TestImages.read(body);
		assertThat(image.getWidth()).isEqualTo(256);
		assertThat(image.getHeight()).isEqualTo(256);

		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}

	@Test
	void 없는_사진이나_형식이_틀린_키는_404이고_폴더_밖은_읽을_수_없다() throws Exception {
		for (String key : List.of("0".repeat(32), "abc", "A".repeat(32), "a".repeat(33))) {
			mockMvc.perform(get("/api/photos/" + key + ".jpg").with(loginAs(kim)))
				.andExpect(status().isNotFound());
		}
		// 인코딩한 경로 구분자는 Spring Security 방화벽이 먼저 막는다.
		mockMvc.perform(get("/api/photos/..%2F..%2Fetc%2Fpasswd.jpg").with(loginAs(kim)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 다시_올리면_주소가_바뀌고_옛_파일은_지워진다() throws Exception {
		String first = photoUrl(upload(kim, TestImages.jpeg(512, 512)));
		String second = photoUrl(upload(kim, TestImages.jpeg(300, 300)));

		assertThat(second).isNotEqualTo(first);
		assertThat(photoFiles()).containsExactly(fileName(second));
		mockMvc.perform(get(first).with(loginAs(kim))).andExpect(status().isNotFound());
	}

	@Test
	void 구글_사진으로_되돌리면_파일을_지우고_여러_번_해도_된다() throws Exception {
		upload(kim, TestImages.jpeg(512, 512));

		resetPhoto(kim)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileImageUrl").value(KIM_GOOGLE_PHOTO))
			.andExpect(jsonPath("$.customPhoto").value(false));
		assertThat(photoFiles()).isEmpty();
		resetPhoto(kim).andExpect(status().isOk());
	}

	@Test
	void 다시_로그인해도_올린_사진은_그대로이고_구글_사진만_갱신된다() throws Exception {
		String url = photoUrl(upload(kim, TestImages.jpeg(512, 512)));

		userService.login("sub-kim", "kim@example.com", true, "김철수", "https://lh3.googleusercontent.com/kim-new");

		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.profileImageUrl").value(url))
			.andExpect(jsonPath("$.googleProfileImageUrl").value("https://lh3.googleusercontent.com/kim-new"));
	}

	@Test
	void 사진을_바꾸는_순간_로그인이_겹쳐도_로그인이_옛_사진으로_되돌리지_않는다() {
		String key = "a".repeat(32);
		// 로그인이 회원을 읽은 뒤, 커밋하기 전에 사진이 바뀐 상황
		transactionTemplate.executeWithoutResult(status -> {
			User user = userRepository.findById(kim.getId()).orElseThrow();
			jdbcTemplate.update("update users set photo_key = ? where id = ?", key, kim.getId());
			user.updateProfile("kim@example.com", "김철수", "https://lh3.googleusercontent.com/kim-new");
			userRepository.flush();
		});

		assertThat(jdbcTemplate.queryForObject("select photo_key from users where id = ?", String.class, kim.getId()))
			.isEqualTo(key);
	}

	@Test
	void 잘못된_요청은_400이고_파일과_사진은_그대로다() throws Exception {
		upload(kim, "그림이 아님".getBytes())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요."));
		upload(kim, new byte[0])
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진 파일을 보내 주세요."));
		upload(kim, TestImages.hugePng(3000))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진이 너무 커요. 다른 사진을 골라 주세요."));
		mockMvc.perform(multipart("/api/me/photo")
			.file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", TestImages.jpeg(10, 10)))
			.with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진 파일을 보내 주세요."));
		mockMvc.perform(post("/api/me/photo").with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진 파일을 보내 주세요."));

		assertThat(photoFiles()).isEmpty();
		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.profileImageUrl").value(KIM_GOOGLE_PHOTO))
			.andExpect(jsonPath("$.customPhoto").value(false));
	}

	@Test
	void 로그인하지_않으면_401이고_XSRF_토큰이_없으면_403() throws Exception {
		MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", TestImages.jpeg(10, 10));
		mockMvc.perform(multipart("/api/me/photo").file(photo).with(xsrf())).andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/me/photo").file(photo).with(loginAs(kim))).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/me/photo").with(xsrf())).andExpect(status().isUnauthorized());
		assertThat(photoFiles()).isEmpty();
	}

	private ResultActions upload(User user, byte[] bytes) throws Exception {
		return mockMvc.perform(multipart("/api/me/photo")
			.file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", bytes))
			.with(loginAs(user)).with(xsrf()));
	}

	private ResultActions resetPhoto(User user) throws Exception {
		return mockMvc.perform(delete("/api/me/photo").with(loginAs(user)).with(xsrf()));
	}

	private static String photoUrl(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.profileImageUrl");
	}

	private static String fileName(String url) {
		return url.substring(url.lastIndexOf('/') + 1);
	}
}
