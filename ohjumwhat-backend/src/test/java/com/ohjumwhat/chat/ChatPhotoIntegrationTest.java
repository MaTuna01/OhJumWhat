package com.ohjumwhat.chat;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.TestImages;
import com.ohjumwhat.organization.InviteService;
import com.ohjumwhat.organization.OrganizationResponse;
import com.ohjumwhat.organization.OrganizationService;
import com.ohjumwhat.poll.PollRequest;
import com.ohjumwhat.poll.PollService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 채팅 사진: 보내기(POST …/messages/photo), 보기(GET /api/chat-photos/…), 고치기 거절, 지우기, 30일 보관, 정리 작업 */
class ChatPhotoIntegrationTest extends IntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	InviteService inviteService;

	@Autowired
	PollService pollService;

	@Autowired
	ChatHub chatHub;

	@Autowired
	ChatPhotoStorage chatPhotoStorage;

	@Autowired
	ChatPhotoJanitor janitor;

	User admin;

	User kim;

	User lee;

	User park;

	Long orgId;

	/** 개발팀 점심, 11:50 마감 → 채팅은 12:50까지 */
	Long pollId;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		User adminUser = new User("sub-admin", "admin@example.com", "관리자", null);
		adminUser.promote();
		admin = userRepository.save(adminUser);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
		park = userRepository.save(new User("sub-park", "park@example.com", "박민수", null));
		OrganizationResponse dev = organizationService.create(kim.getId(), "개발팀");
		orgId = dev.id();
		inviteService.join(dev.inviteToken(), lee.getId());
		organizationService.create(park.getId(), "디자인팀");
		pollId = pollService.create(orgId, kim.getId(), new PollRequest("점심", "11:50")).id();
	}

	@AfterEach
	void closeConnections() {
		chatHub.connectedPollIds().forEach(id -> chatHub.closePoll(id, ChatHub.NOT_ALLOWED));
	}

	@Test
	void 사진을_보내면_원본과_썸네일을_만들고_같은_투표를_보는_사람에게_보낸다() throws Exception {
		FakeWebSocketSession leeTab = new FakeWebSocketSession(pollId, orgId, lee.getId(), "session-lee");
		chatHub.register(leeTab);

		String sent = sendPhoto(kim, TestImages.jpeg(2000, 1000))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value(nullValue()))
			.andExpect(jsonPath("$.author.name").value("김철수"))
			.andExpect(jsonPath("$.photo.width").value(1600))
			.andExpect(jsonPath("$.photo.height").value(800))
			.andExpect(jsonPath("$.photo.expired").value(false))
			.andExpect(jsonPath("$.deleted").value(false))
			.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(sent, "$.photo.url");
		String thumbnailUrl = JsonPath.read(sent, "$.photo.thumbnailUrl");
		assertThat(url).matches("/api/chat-photos/[0-9a-f]{32}\\.jpg");
		assertThat(thumbnailUrl).isEqualTo(url.replace(".jpg", "_t.jpg"));
		assertThat(chatPhotoFiles()).hasSize(2);

		assertThat(leeTab.texts()).hasSize(1);
		assertThat(leeTab.texts().getFirst()).contains("\"type\":\"created\"").contains(thumbnailUrl);

		byte[] full = mockMvc.perform(get(url).with(loginAs(lee)))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("Cache-Control", "max-age=2592000, private"))
			.andReturn().getResponse().getContentAsByteArray();
		BufferedImage fullImage = TestImages.read(full);
		assertThat(fullImage.getWidth()).isEqualTo(1600);
		assertThat(fullImage.getHeight()).isEqualTo(800);
		BufferedImage thumbnail = TestImages.read(
				mockMvc.perform(get(thumbnailUrl).with(loginAs(kim))).andReturn().getResponse().getContentAsByteArray());
		assertThat(thumbnail.getWidth()).isEqualTo(480);
		assertThat(thumbnail.getHeight()).isEqualTo(240);

		mockMvc.perform(get("/api/polls/" + pollId + "/messages").with(loginAs(lee)))
			.andExpect(jsonPath("$.messages", hasSize(1)))
			.andExpect(jsonPath("$.messages[0].photo.thumbnailUrl").value(thumbnailUrl));
		// 사진도 안 읽은 메시지다(보낸 사람은 읽은 것으로 한다).
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/today").with(loginAs(lee)))
			.andExpect(jsonPath("$[0].unreadMessages").value(1));
		mockMvc.perform(get("/api/orgs/" + orgId + "/polls/today").with(loginAs(kim)))
			.andExpect(jsonPath("$[0].unreadMessages").value(0));
	}

	@Test
	void 작은_사진은_키우지_않는다() throws Exception {
		String sent = sendPhoto(kim, TestImages.transparentPng(300, 400))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.photo.width").value(300))
			.andExpect(jsonPath("$.photo.height").value(400))
			.andReturn().getResponse().getContentAsString();
		BufferedImage thumbnail = TestImages.read(mockMvc
			.perform(get((String) JsonPath.read(sent, "$.photo.thumbnailUrl")).with(loginAs(kim)))
			.andReturn().getResponse().getContentAsByteArray());
		assertThat(thumbnail.getWidth()).isEqualTo(300);
		// 투명한 곳은 흰 바탕이 된다(JPEG).
		assertThat(thumbnail.getRGB(1, 1) & 0xFFFFFF).isEqualTo(0xFFFFFF);
	}

	@Test
	void 사진은_그_조직_멤버와_관리자만_본다() throws Exception {
		String url = photoUrl(sendPhoto(kim, TestImages.jpeg(100, 100)));

		mockMvc.perform(get(url).with(loginAs(admin))).andExpect(status().isOk());
		mockMvc.perform(get(url).with(loginAs(park))).andExpect(status().isNotFound());
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/chat-photos/" + "0".repeat(32) + ".jpg").with(loginAs(kim)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/chat-photos/abc.jpg").with(loginAs(kim))).andExpect(status().isNotFound());
	}

	@Test
	void 멤버가_아니거나_채팅이_닫혔거나_사진이_아니면_보내지_못한다() throws Exception {
		sendPhoto(park, TestImages.jpeg(100, 100)).andExpect(status().isNotFound());
		sendPhoto(kim, TestImages.gif(100, 100))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요."));
		sendPhoto(kim, TestImages.hugePng(3000))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진이 너무 커요. 다른 사진을 골라 주세요."));
		sendPhoto(kim, new byte[0])
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진 파일을 보내 주세요."));
		mockMvc.perform(post(photoSendUrl()).with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진 파일을 보내 주세요."));

		clock.set(2026, 9, 30, 12, 50);
		sendPhoto(kim, TestImages.jpeg(100, 100))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("채팅이 닫혔어요."));
		assertThat(chatPhotoFiles()).isEmpty();
		mockMvc.perform(multipart(photoSendUrl()).file(photoPart(TestImages.jpeg(10, 10))).with(loginAs(kim)))
			.andExpect(status().isForbidden());
	}

	@Test
	void 사진은_고칠_수_없고_지우면_파일도_지운다() throws Exception {
		FakeWebSocketSession leeTab = new FakeWebSocketSession(pollId, orgId, lee.getId(), "session-lee");
		chatHub.register(leeTab);
		ResultActions sent = sendPhoto(kim, TestImages.jpeg(100, 100));
		String url = photoUrl(sent);
		Number id = JsonPath.read(sent.andReturn().getResponse().getContentAsString(), "$.id");

		mockMvc.perform(put(messageUrl(id)).with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"글로 바꾸기\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사진은 고칠 수 없어요."));

		mockMvc.perform(delete(messageUrl(id)).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deleted").value(true))
			.andExpect(jsonPath("$.photo").value(nullValue()))
			.andExpect(jsonPath("$.body").value(nullValue()));
		assertThat(chatPhotoFiles()).isEmpty();
		mockMvc.perform(get(url).with(loginAs(kim))).andExpect(status().isNotFound());
		assertThat(leeTab.texts().getLast()).contains("\"type\":\"deleted\"").contains("\"photo\":null");
	}

	@Test
	void 관리자가_지워도_파일을_지운다() throws Exception {
		Number id = JsonPath.read(
				sendPhoto(kim, TestImages.jpeg(100, 100)).andReturn().getResponse().getContentAsString(), "$.id");
		clock.set(2026, 10, 2, 9, 0); // 채팅이 닫힌 뒤에도
		mockMvc.perform(delete("/api/admin/chat-messages/" + id).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photo").value(nullValue()));
		assertThat(chatPhotoFiles()).isEmpty();
	}

	@Test
	void 사진은_30일_동안만_보인다() throws Exception {
		String url = photoUrl(sendPhoto(kim, TestImages.jpeg(100, 50)));

		clock.set(2026, 10, 30, 10, 59); // 30일이 되기 1분 전
		mockMvc.perform(get(url).with(loginAs(lee))).andExpect(status().isOk());

		clock.set(2026, 10, 30, 11, 0);
		mockMvc.perform(get(url).with(loginAs(lee))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/polls/" + pollId + "/messages").with(loginAs(lee)))
			.andExpect(jsonPath("$.messages[0].photo.expired").value(true))
			.andExpect(jsonPath("$.messages[0].photo.width").value(100))
			.andExpect(jsonPath("$.messages[0].photo.height").value(50))
			.andExpect(jsonPath("$.messages[0].photo.url").value(nullValue()))
			.andExpect(jsonPath("$.messages[0].photo.thumbnailUrl").value(nullValue()))
			.andExpect(jsonPath("$.messages[0].deleted").value(false));
	}

	@Test
	void 정리_작업은_더는_보여주지_않는_사진의_파일만_지운다() throws Exception {
		String live = key(photoUrl(sendPhoto(kim, TestImages.jpeg(100, 100))));
		String removed = key(photoUrl(sendPhoto(lee, TestImages.jpeg(100, 100))));
		Long otherPollId = pollService.create(orgId, kim.getId(), new PollRequest("저녁", "18:00")).id();
		String cascaded = key(photoUrl(sendPhoto(kim, TestImages.jpeg(100, 100), otherPollId)));
		// 지운 투표의 메시지는 CASCADE로 없어지고 파일만 남는다.
		mockMvc.perform(delete("/api/polls/" + otherPollId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());
		// 저장에 실패해 아무도 가리키지 않는 파일
		String orphan = "f".repeat(32);
		Files.write(chatPhotoStorage.dir().resolve(orphan + ".jpg"), TestImages.jpeg(10, 10));
		Files.write(chatPhotoStorage.dir().resolve(orphan + "_t.jpg"), TestImages.jpeg(10, 10));
		Files.write(chatPhotoStorage.dir().resolve("abc.tmp"), new byte[] { 1 });
		Number removedId = JsonPath.read(mockMvc.perform(get("/api/polls/" + pollId + "/messages").with(loginAs(lee)))
			.andReturn().getResponse().getContentAsString(), "$.messages[1].id");
		jdbcTemplate.update("UPDATE chat_messages SET body = NULL, image_key = NULL, image_width = NULL, "
				+ "image_height = NULL, deleted_at = now() WHERE id = ?", removedId.longValue());
		// 방금 쓴 파일(1시간 안)은 그대로 둔다.
		String fresh = "e".repeat(32);
		Files.write(chatPhotoStorage.dir().resolve(fresh + ".jpg"), TestImages.jpeg(10, 10));
		ageFilesExcept(Duration.ofHours(2), fresh + ".jpg");

		assertThat(janitor.clean()).isEqualTo(7);
		assertThat(chatPhotoFiles()).containsExactlyInAnyOrder(fresh + ".jpg", live + ".jpg", live + "_t.jpg");

		// 30일이 지나면 남은 사진도 지운다.
		clock.set(2026, 10, 30, 11, 0);
		ageFilesExcept(Duration.ofHours(2), "");
		janitor.clean();
		assertThat(chatPhotoFiles()).isEmpty();
	}

	@Test
	void 사진은_한_사람이_10분에_20장까지_보낸다() throws Exception {
		for (int i = 0; i < ChatPhotoRateLimiter.LIMIT; i++) {
			sendPhoto(kim, TestImages.jpeg(10, 10)).andExpect(status().isCreated());
			clock.set(Instant.now(clock).plusSeconds(2));
		}
		sendPhoto(kim, TestImages.jpeg(10, 10))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.message").value("사진을 너무 많이 보내고 있어요. 잠시 후 다시 보내 주세요."));
		// 글은 그대로 보낸다.
		mockMvc.perform(post("/api/polls/" + pollId + "/messages").with(loginAs(kim)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"사진은 여기까지\"}"))
			.andExpect(status().isCreated());
	}

	private ResultActions sendPhoto(User user, byte[] bytes) throws Exception {
		return sendPhoto(user, bytes, pollId);
	}

	private ResultActions sendPhoto(User user, byte[] bytes, Long poll) throws Exception {
		return mockMvc.perform(multipart("/api/polls/" + poll + "/messages/photo").file(photoPart(bytes))
			.with(loginAs(user)).with(xsrf()));
	}

	private static MockMultipartFile photoPart(byte[] bytes) {
		return new MockMultipartFile("photo", "photo.jpg", "image/jpeg", bytes);
	}

	private static String photoUrl(ResultActions sent) throws Exception {
		return JsonPath.read(sent.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.photo.url");
	}

	private static String key(String url) {
		return url.substring(url.lastIndexOf('/') + 1, url.length() - ".jpg".length());
	}

	private String photoSendUrl() {
		return "/api/polls/" + pollId + "/messages/photo";
	}

	private String messageUrl(Number id) {
		return "/api/polls/" + pollId + "/messages/" + id;
	}

	/** 파일을 시계 기준 age만큼 전에 쓴 것으로 한다(정리 작업은 1시간 안의 파일을 건드리지 않는다). */
	private void ageFilesExcept(Duration age, String keep) throws IOException {
		FileTime time = FileTime.from(Instant.now(clock).minus(age));
		try (Stream<Path> files = Files.list(chatPhotoStorage.dir())) {
			for (Path file : files.toList()) {
				if (!file.getFileName().toString().equals(keep)) {
					Files.setLastModifiedTime(file, time);
				}
			}
		}
	}

	private List<String> chatPhotoFiles() throws IOException {
		if (!Files.isDirectory(chatPhotoStorage.dir())) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(chatPhotoStorage.dir())) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}
}
