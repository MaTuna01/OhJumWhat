package com.ohjumwhat.sanction;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import com.ohjumwhat.TestImages;
import com.ohjumwhat.user.ProfilePhotoService;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserService;

/** 관리자 콘솔의 제재: 권한·확인, 걸기(정리·초기화), 목록·상태, 해제, 개요·회원 목록의 「제한 중」, 강제 탈퇴 */
class SanctionAdminIntegrationTest extends SanctionTestBase {

	@Autowired
	UserService userService;

	@Autowired
	SanctionService sanctionService;

	@Autowired
	ProfilePhotoService profilePhotoService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void 관리자만_쓰고_로그인하지_않으면_401_일반_회원은_403이다() throws Exception {
		String json = "{\"restrictions\": [\"CHAT\"], \"reason\": \"ABUSE\"}";
		mockMvc.perform(post("/api/admin/users/{id}/sanctions", lee.getId()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)).andExpect(status().isUnauthorized());
		apply(kim, lee, json).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없어요."));
		mockMvc.perform(get("/api/admin/sanctions").with(loginAs(kim))).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/users/{id}/sanctions", lee.getId()).with(loginAs(kim)))
			.andExpect(status().isForbidden());
		long sanctionId = restrict(lee, 7, Restriction.CHAT);
		mockMvc.perform(post("/api/admin/sanctions/{id}/lift", sanctionId).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isForbidden());
		assertThat(sanctionRepository.findById(sanctionId).orElseThrow().getLiftedAt()).isNull();
	}

	@Test
	void 없는_회원은_404_나_자신과_관리자는_409다() throws Exception {
		String json = "{\"restrictions\": [\"CHAT\"], \"reason\": \"ABUSE\"}";
		User missing = userRepository.save(new User("sub-gone", "gone@example.com", "없는 사람", null));
		userRepository.delete(missing);
		apply(admin, missing, json).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("회원을 찾을 수 없어요."));
		mockMvc.perform(get("/api/admin/users/{id}/sanctions", missing.getId()).with(loginAs(admin)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("회원을 찾을 수 없어요."));

		apply(admin, admin, json).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("자기 자신은 제재할 수 없어요."));
		User otherAdmin = new User("sub-admin2", "admin2@example.com", "부관리자", null);
		otherAdmin.promote();
		otherAdmin = userRepository.save(otherAdmin);
		apply(admin, otherAdmin, json).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("관리자는 제재할 수 없어요."));
		// 확인보다 먼저 409다(나·관리자에게는 값이 틀려도 걸 수 없다).
		apply(admin, otherAdmin, "{\"restrictions\": [\"BAN\"]}").andExpect(status().isConflict());

		mockMvc.perform(post("/api/admin/sanctions/{id}/lift", 999).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("제재를 찾을 수 없어요."));
		assertThat(sanctionRepository.count()).isZero();
	}

	@Test
	void 값이_틀리면_400이고_아무것도_바꾸지_않는다() throws Exception {
		jdbcTemplate.update("update users set nickname = '철수' where id = ?", kim.getId());
		Map<String, String> cases = Map.of(
				"{\"restrictions\": [\"BAN\"], \"reason\": \"ABUSE\"}", "제한할 기능을 다시 골라 주세요.",
				"{\"restrictions\": [null], \"reason\": \"ABUSE\"}", "제한할 기능을 다시 골라 주세요.",
				"{\"resets\": [\"NICKNAME\", \"EMAIL\"], \"reason\": \"ABUSE\"}", "초기화할 항목을 다시 골라 주세요.",
				"{\"restrictions\": [\"CHAT\"], \"resets\": [\"NICKNAME\"]}", "사유를 골라 주세요.",
				"{\"restrictions\": [\"CHAT\"], \"reason\": \"RUDE\"}", "사유를 골라 주세요.",
				"{\"restrictions\": [\"CHAT\"], \"days\": 2, \"reason\": \"ABUSE\"}", "기간은 1일·3일·7일·30일 중에서 골라 주세요.",
				"{\"resets\": [\"NICKNAME\"], \"days\": 7, \"reason\": \"PROFILE\"}", "제한을 고르지 않으면 기간을 정할 수 없어요.",
				"{\"resets\": [\"NICKNAME\"], \"reason\": \"PROFILE\", \"note\": \"" + "가".repeat(201) + "\"}",
				"200자 이하로 입력해 주세요.",
				"{\"resets\": [\"NICKNAME\"], \"reason\": \"PROFILE\", \"note\": \"설명\\u0007\"}", "쓸 수 없는 문자가 있어요.");
		for (Map.Entry<String, String> c : cases.entrySet()) {
			apply(admin, kim, c.getKey()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(c.getValue()));
		}
		assertThat(sanctionRepository.count()).isZero();
		assertThat(userRepository.findById(kim.getId()).orElseThrow().getNickname()).isEqualTo("철수");
	}

	@Test
	void 제한은_중복을_없애_정의_순서로_두고_활동_정지는_혼자만_남긴다() throws Exception {
		jdbcTemplate.update("update users set nickname = '철수' where id = ?", kim.getId());
		apply(admin, kim, """
				{"restrictions": ["LETTER", "CHAT", "LETTER"], "resets": [], "days": 3, "reason": "SPAM",
				 "note": "  같은 쪽지를 여러 번\\n보냈어요  "}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.userId").value(kim.getId()))
			.andExpect(jsonPath("$.userName").value("철수"))
			.andExpect(jsonPath("$.userEmail").value("kim@example.com"))
			.andExpect(jsonPath("$.userProfileImageUrl").value("https://lh3.googleusercontent.com/kim"))
			.andExpect(jsonPath("$.restrictions", contains("CHAT", "LETTER")))
			.andExpect(jsonPath("$.resets", empty()))
			.andExpect(jsonPath("$.reason").value("SPAM"))
			.andExpect(jsonPath("$.note").value("같은 쪽지를 여러 번\n보냈어요"))
			.andExpect(jsonPath("$.createdAt").value("2026-10-07T02:00:00Z"))
			.andExpect(jsonPath("$.endsAt").value("2026-10-10T02:00:00Z"))
			.andExpect(jsonPath("$.liftedAt").value(nullValue()))
			.andExpect(jsonPath("$.createdByName").value("관리자"))
			.andExpect(jsonPath("$.liftedByName").value(nullValue()))
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		apply(admin, kim, """
				{"restrictions": ["CHAT", "SUSPEND", "PROFILE"], "days": null, "reason": "ABUSE", "note": "   "}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restrictions", contains("SUSPEND")))
			.andExpect(jsonPath("$.endsAt").value(nullValue()))
			.andExpect(jsonPath("$.note").value(nullValue()))
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		// 값을 빼면 빈 목록이고, 아무것도 고르지 않으면 경고다.
		apply(admin, kim, "{\"reason\": \"ETC\", \"note\": \"주의해 주세요\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restrictions", empty()))
			.andExpect(jsonPath("$.resets", empty()))
			.andExpect(jsonPath("$.endsAt").value(nullValue()))
			.andExpect(jsonPath("$.status").value("WARNING"));
	}

	@Test
	void 별명_소개_상세_프로필을_비우고_사진은_커밋한_뒤_파일까지_지운다() throws Exception {
		jdbcTemplate.update("update users set nickname = '철수' where id = ?", kim.getId());
		userService.changeIntro(kim.getId(), "점심 좋아요", List.of("김치찌개", "라멘"));
		userService.changeDetails(kim.getId(), "ENFP", "AUTUMN_WARM", List.of("등산"), 32, "사원");
		profilePhotoService.upload(kim.getId(), TestImages.jpeg(300, 300));
		String photoKey = userRepository.findById(kim.getId()).orElseThrow().getPhotoKey();
		assertThat(photoFiles()).containsExactly(photoKey + ".jpg");

		apply(admin, kim, """
				{"resets": ["DETAILS", "PHOTO", "NICKNAME", "INTRO", "PHOTO"], "reason": "PROFILE",
				 "note": "프로필 사진이 부적절해요"}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restrictions", empty()))
			.andExpect(jsonPath("$.resets", contains("NICKNAME", "PHOTO", "INTRO", "DETAILS")))
			.andExpect(jsonPath("$.status").value("RESET_ONLY"))
			// 응답의 이름·사진은 초기화한 뒤의 것이다.
			.andExpect(jsonPath("$.userName").value("김철수"))
			.andExpect(jsonPath("$.userProfileImageUrl").value("https://lh3.googleusercontent.com/kim"));

		User reset = userRepository.findById(kim.getId()).orElseThrow();
		assertThat(reset.getNickname()).isNull();
		assertThat(reset.getPhotoKey()).isNull();
		assertThat(reset.getBio()).isNull();
		assertThat(reset.getFoodTags()).isEmpty();
		assertThat(reset.getDetails()).isNull();
		// 상세 프로필은 다섯 항목을 함께 비운다(ck_users_profile_details: 모두 비었거나 모두 채워졌거나).
		assertThat(jdbcTemplate.queryForMap(
				"select mbti, personal_color, age, job_title, cardinality(hobbies) as hobbies from users where id = ?",
				kim.getId()))
			.containsEntry("mbti", null)
			.containsEntry("personal_color", null)
			.containsEntry("age", null)
			.containsEntry("job_title", null)
			.containsEntry("hobbies", 0);
		assertThat(photoFiles()).isEmpty();
		mockMvc.perform(get("/api/me").with(loginAs(kim)))
			.andExpect(jsonPath("$.name").value("김철수"))
			.andExpect(jsonPath("$.customPhoto").value(false))
			.andExpect(jsonPath("$.details").value(nullValue()))
			// 초기화만 한 제재는 지금 걸려 있는 제한이 아니다.
			.andExpect(jsonPath("$.sanctions", empty()));
	}

	@Test
	void 비어_있는_항목은_초기화하지_않고_기록에서도_뺀다() throws Exception {
		// 모두 비어 있으면 초기화할 것이 없어 경고만 남는다(본인 안내에 하지 않은 초기화가 보이지 않게).
		apply(admin, kim, "{\"resets\": [\"NICKNAME\", \"PHOTO\", \"INTRO\", \"DETAILS\"], \"reason\": \"PROFILE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resets", empty()))
			.andExpect(jsonPath("$.status").value("WARNING"));
		assertThat(userRepository.findById(kim.getId()).orElseThrow().getDetails()).isNull();

		// 채워진 항목만 남는다.
		jdbcTemplate.update("update users set nickname = '바보' where id = ?", kim.getId());
		apply(admin, kim, "{\"resets\": [\"NICKNAME\", \"PHOTO\"], \"reason\": \"PROFILE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resets", contains("NICKNAME")))
			.andExpect(jsonPath("$.status").value("RESET_ONLY"));
		assertThat(userRepository.findById(kim.getId()).orElseThrow().getNickname()).isNull();
	}

	@Test
	void 사진은_제재가_롤백되면_지우지_않는다() throws Exception {
		profilePhotoService.upload(kim.getId(), TestImages.jpeg(300, 300));
		String photoKey = userRepository.findById(kim.getId()).orElseThrow().getPhotoKey();

		// 같은 트랜잭션 안에서 걸고 롤백하면 파일은 남는다(커밋한 뒤에만 지운다).
		transactionTemplate.executeWithoutResult(status -> {
			sanctionService.apply(admin.getId(), kim.getId(), List.of(), List.of("PHOTO"), null,
					"PROFILE", null);
			assertThat(photoFilesUnchecked()).containsExactly(photoKey + ".jpg");
			status.setRollbackOnly();
		});

		assertThat(photoFiles()).containsExactly(photoKey + ".jpg");
		assertThat(userRepository.findById(kim.getId()).orElseThrow().getPhotoKey()).isEqualTo(photoKey);
		assertThat(sanctionRepository.count()).isZero();
	}

	@Test
	void 목록은_최신순이고_상태를_지금_시각으로_정한다() throws Exception {
		fillProfile(kim);
		long expired = restrict(kim, 1, Restriction.CHAT);
		long lifted = restrict(kim, 7, Restriction.LETTER);
		long warning = sanction(kim, "{\"reason\": \"ETC\"}");
		long resetOnly = sanction(kim, "{\"resets\": [\"NICKNAME\"], \"reason\": \"PROFILE\"}");
		long active = restrict(lee, null, Restriction.GUESTBOOK);

		clock.set(2026, 10, 8, 12, 0); // 하루 뒤: 채팅 제한은 끝났다
		lift(lifted).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LIFTED"))
			.andExpect(jsonPath("$.liftedAt").value("2026-10-08T03:00:00Z"))
			.andExpect(jsonPath("$.liftedByName").value("관리자"));

		mockMvc.perform(get("/api/admin/sanctions").param("status", "all").with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", contains((int) active, (int) resetOnly, (int) warning, (int) lifted,
					(int) expired)))
			.andExpect(jsonPath("$[*].status", contains("ACTIVE", "RESET_ONLY", "WARNING", "LIFTED", "EXPIRED")))
			.andExpect(jsonPath("$[0].userName").value("이영희"))
			.andExpect(jsonPath("$[0].userProfileImageUrl").value(nullValue()));
		// 기본은 진행 중인 것만
		mockMvc.perform(get("/api/admin/sanctions").with(loginAs(admin)))
			.andExpect(jsonPath("$[*].id", contains((int) active)));
		mockMvc.perform(get("/api/admin/sanctions").param("status", "active").with(loginAs(admin)))
			.andExpect(jsonPath("$[*].id", contains((int) active)));
		mockMvc.perform(get("/api/admin/users/{id}/sanctions", kim.getId()).with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", contains((int) resetOnly, (int) warning, (int) lifted, (int) expired)));
		mockMvc.perform(get("/api/admin/users/{id}/sanctions", admin.getId()).with(loginAs(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", empty()));
	}

	@Test
	void 진행_중인_제재만_해제하고_초기화는_되돌리지_않는다() throws Exception {
		jdbcTemplate.update("update users set nickname = '철수' where id = ?", kim.getId());
		long sanctionId = sanction(kim,
				"{\"restrictions\": [\"PROFILE\"], \"resets\": [\"NICKNAME\"], \"days\": 7, \"reason\": \"PROFILE\"}");

		clock.set(2026, 10, 7, 12, 0);
		lift(sanctionId).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LIFTED"))
			.andExpect(jsonPath("$.endsAt").value("2026-10-14T02:00:00Z"));
		lift(sanctionId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 끝났거나 해제된 제재예요."));
		assertThat(userRepository.findById(kim.getId()).orElseThrow().getNickname()).isNull();

		long warning = sanction(kim, "{\"reason\": \"ETC\"}");
		lift(warning).andExpect(status().isConflict());
		long resetOnly = sanction(kim, "{\"resets\": [\"INTRO\"], \"reason\": \"PROFILE\"}");
		lift(resetOnly).andExpect(status().isConflict());
		long expired = restrict(kim, 1, Restriction.CHAT);
		clock.set(2026, 10, 8, 12, 0);
		lift(expired).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 끝났거나 해제된 제재예요."));
	}

	@Test
	void 개요는_지금_제한된_회원_수를_회원_목록과_상세는_제한_중을_준다() throws Exception {
		restrict(kim, 7, Restriction.CHAT);
		restrict(kim, null, Restriction.LETTER); // 같은 사람은 한 번만 센다.
		long leeChat = restrict(lee, 1, Restriction.CHAT);
		sanction(lee, "{\"reason\": \"ETC\"}"); // 경고는 제한이 아니다.

		mockMvc.perform(get("/api/admin/stats").with(loginAs(admin)))
			.andExpect(jsonPath("$.restrictedUserCount").value(2))
			.andExpect(jsonPath("$.userCount").value(3));
		mockMvc.perform(get("/api/admin/users").with(loginAs(admin)))
			.andExpect(jsonPath("$[?(@.id == " + kim.getId() + ")].restricted", contains(true)))
			.andExpect(jsonPath("$[?(@.id == " + lee.getId() + ")].restricted", contains(true)))
			.andExpect(jsonPath("$[?(@.id == " + admin.getId() + ")].restricted", contains(false)))
			.andExpect(jsonPath("$[0].customPhoto").isBoolean());

		clock.set(2026, 10, 8, 11, 0); // 이영희의 채팅 제한이 끝났다
		mockMvc.perform(get("/api/admin/stats").with(loginAs(admin)))
			.andExpect(jsonPath("$.restrictedUserCount").value(1));
		mockMvc.perform(get("/api/admin/users/{id}", lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.restricted").value(false));
		mockMvc.perform(get("/api/admin/users/{id}", kim.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$.user.restricted").value(true))
			.andExpect(jsonPath("$.user.name").value("김철수"));
		assertThat(sanctionRepository.findById(leeChat).orElseThrow().getEndsAt()).isNotNull();
	}

	@Test
	void 제재가_있어도_강제_탈퇴하면_기록도_함께_지워지고_건_관리자가_지워지면_이름만_비운다() throws Exception {
		User otherAdmin = new User("sub-admin2", "admin2@example.com", "부관리자", null);
		otherAdmin.promote();
		otherAdmin = userRepository.save(otherAdmin);
		restrict(kim, 7, Restriction.CHAT);
		apply(otherAdmin, lee, "{\"restrictions\": [\"LETTER\"], \"reason\": \"ABUSE\"}").andExpect(status().isOk());

		mockMvc.perform(delete("/api/admin/users/{id}", kim.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject("select count(*) from user_sanctions where user_id = ?", Long.class,
				kim.getId())).isZero();

		jdbcTemplate.update("delete from users where id = ?", otherAdmin.getId());
		mockMvc.perform(get("/api/admin/users/{id}/sanctions", lee.getId()).with(loginAs(admin)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].createdByName").value(nullValue()))
			.andExpect(jsonPath("$[0].status").value("ACTIVE"));
	}

	private List<String> photoFilesUnchecked() {
		try {
			return photoFiles();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
