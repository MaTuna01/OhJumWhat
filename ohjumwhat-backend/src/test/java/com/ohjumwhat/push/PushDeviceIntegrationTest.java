package com.ohjumwhat.push;

import static com.ohjumwhat.TestAuth.loginAs;
import static com.ohjumwhat.TestAuth.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import com.ohjumwhat.IntegrationTest;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/** 이 기기에서 알림 받기 켜기·끄기, 그리고 기기가 로그인(세션)과 함께 지워지는지 */
class PushDeviceIntegrationTest extends IntegrationTest {

	static final String PHONE = "fid_phone_0000000001";

	static final String LAPTOP = "fid_laptop_000000002";

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcIndexedSessionRepository sessionRepository;

	User kim;

	User lee;

	@BeforeEach
	void setUp() {
		clock.set(2026, 10, 6, 12, 0);
		kim = userRepository.save(new User("sub-kim", "kim@example.com", "김철수", null));
		lee = userRepository.save(new User("sub-lee", "lee@example.com", "이영희", null));
	}

	@Test
	void 로그인하지_않았으면_401이다() throws Exception {
		mockMvc.perform(put("/api/push/devices/" + PHONE).with(xsrf())).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/push/devices/" + PHONE).with(xsrf())).andExpect(status().isUnauthorized());
	}

	@Test
	void CSRF_토큰이_없으면_403이다() throws Exception {
		String session = loginSession(kim);
		Cookie cookie = new Cookie("SESSION",
				Base64.getEncoder().encodeToString(session.getBytes(StandardCharsets.UTF_8)));

		mockMvc.perform(put("/api/push/devices/" + PHONE).with(loginAs(kim)).cookie(cookie))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/push/devices/" + PHONE).with(loginAs(kim)).cookie(cookie))
			.andExpect(status().isForbidden());
		assertThat(devices()).isEmpty();
	}

	@Test
	void FID_모양이_아니면_400이다() throws Exception {
		String session = loginSession(kim);
		for (String fid : List.of("short", "fid.with.dots.000000", "fid with space 000000", "a".repeat(65))) {
			registerPushDevice(kim, session, fid).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("알림을 받을 기기 정보가 올바르지 않아요."));
		}
		registerPushDevice(kim, session, "a".repeat(16)).andExpect(status().isOk());
		registerPushDevice(kim, session, "A-z_9".repeat(12) + "abcd").andExpect(status().isOk());
		assertThat(devices()).hasSize(2);
	}

	@Test
	void 푸시가_꺼져_있으면_409다() throws Exception {
		pushSender.setEnabled(false);

		registerPushDevice(kim, loginSession(kim), PHONE).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("지금은 알림을 켤 수 없어요."));
		assertThat(devices()).isEmpty();
	}

	@Test
	void 로그인_세션_행이_없으면_401이다() throws Exception {
		// MockMvc의 loginAs는 보안 컨텍스트만 넣고 세션 행(spring_session)은 만들지 않는다.
		mockMvc.perform(put("/api/push/devices/" + PHONE).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("다시 로그인해 주세요."));
		assertThat(jdbcTemplate.queryForObject("select count(*) from spring_session", Long.class)).isZero();

		// 쿠키의 세션이 DB에 없다(만료돼 지워졌다).
		registerPushDevice(kim, "00000000-0000-0000-0000-000000000000", PHONE).andExpect(status().isUnauthorized());
		assertThat(devices()).isEmpty();
	}

	@Test
	void 처음_켜면_created가_true고_다시_켜면_false다() throws Exception {
		String session = loginSession(kim);

		registerPushDevice(kim, session, PHONE).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(true));
		clock.set(2026, 10, 6, 13, 0);
		registerPushDevice(kim, session, PHONE).andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(false));

		assertThat(devices()).containsExactly(new Device(kim.getId(), PHONE, primaryId(session)));
		assertThat(jdbcTemplate.queryForObject("select last_seen_at > created_at from push_devices", Boolean.class))
			.isTrue();
	}

	@Test
	void 같은_브라우저에서_다른_계정이_켜면_기기가_그_사람에게_옮겨_간다() throws Exception {
		String kimSession = loginSession(kim);
		String leeSession = loginSession(lee);
		registerPushDevice(kim, kimSession, PHONE).andExpect(jsonPath("$.created").value(true));

		registerPushDevice(lee, leeSession, PHONE).andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(false));

		assertThat(devices()).containsExactly(new Device(lee.getId(), PHONE, primaryId(leeSession)));
	}

	@Test
	void 끄면_내_기기만_지운다() throws Exception {
		registerPushDevice(kim, loginSession(kim), PHONE).andExpect(status().isOk());

		mockMvc.perform(delete("/api/push/devices/" + PHONE).with(loginAs(lee)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(devices()).hasSize(1);

		mockMvc.perform(delete("/api/push/devices/" + PHONE).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());
		assertThat(devices()).isEmpty();
		// 없어도 204
		mockMvc.perform(delete("/api/push/devices/" + PHONE).with(loginAs(kim)).with(xsrf()))
			.andExpect(status().isNoContent());
	}

	@Test
	void 로그아웃하면_그_로그인의_기기만_지운다() throws Exception {
		String phoneSession = loginSession(kim);
		String laptopSession = loginSession(kim);
		registerPushDevice(kim, phoneSession, PHONE).andExpect(status().isOk());
		registerPushDevice(kim, laptopSession, LAPTOP).andExpect(status().isOk());

		mockMvc.perform(post("/logout").with(loginAs(kim)).with(xsrf(phoneSession)))
			.andExpect(status().isNoContent());

		assertThat(devices()).containsExactly(new Device(kim.getId(), LAPTOP, primaryId(laptopSession)));
	}

	@Test
	void 세션이_만료돼_지워지면_기기도_지워진다() throws Exception {
		String session = loginSession(kim);
		registerPushDevice(kim, session, PHONE).andExpect(status().isOk());

		// Spring Session이 주기적으로 만료된 세션 행을 지운다.
		jdbcTemplate.update("update spring_session set expiry_time = 0 where session_id = ?", session);
		sessionRepository.cleanUpExpiredSessions();

		assertThat(devices()).isEmpty();
	}

	@Test
	void 강제_탈퇴하면_기기도_지워진다() throws Exception {
		User admin = new User("sub-admin", "admin@example.com", "관리자", null);
		admin.promote();
		admin = userRepository.save(admin);
		registerPushDevice(kim, loginSession(kim), PHONE).andExpect(status().isOk());
		registerPushDevice(lee, loginSession(lee), LAPTOP).andExpect(status().isOk());

		mockMvc.perform(delete("/api/admin/users/" + kim.getId()).with(loginAs(admin)).with(xsrf()))
			.andExpect(status().isNoContent());

		assertThat(devices()).extracting(Device::fid).containsExactly(LAPTOP);
	}

	@Test
	void 한_사람당_최근_10대까지만_남긴다() throws Exception {
		String session = loginSession(kim);
		for (int i = 1; i <= 11; i++) {
			clock.set(2026, 10, 6, 12, i);
			registerPushDevice(kim, session, "fid_device_%010d".formatted(i)).andExpect(status().isOk());
		}
		assertThat(devices()).hasSize(10).extracting(Device::fid).doesNotContain("fid_device_0000000001");

		// 오래된 기기를 다시 켜면(마지막 등록 시각이 바뀌면) 그다음으로 오래된 기기가 빠진다.
		clock.set(2026, 10, 6, 13, 0);
		registerPushDevice(kim, session, "fid_device_0000000002").andExpect(status().isOk());
		registerPushDevice(kim, session, "fid_device_0000000012").andExpect(status().isOk());
		assertThat(devices()).hasSize(10)
			.extracting(Device::fid)
			.contains("fid_device_0000000002", "fid_device_0000000012")
			.doesNotContain("fid_device_0000000003");

		// 다른 사람의 기기는 세지 않는다.
		registerPushDevice(lee, loginSession(lee), LAPTOP).andExpect(status().isOk());
		assertThat(devices()).hasSize(11);
	}

	@Test
	void 설정의_push는_푸시가_켜져_있을_때만_준다() throws Exception {
		mockMvc.perform(get("/api/config").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.push.apiKey").value("test-api-key"))
			.andExpect(jsonPath("$.push.projectId").value("test-project"))
			.andExpect(jsonPath("$.push.appId").value("test-app-id"))
			.andExpect(jsonPath("$.push.messagingSenderId").value("1234567890"))
			.andExpect(jsonPath("$.push.vapidKey").value("test-vapid-key"))
			.andExpect(jsonPath("$.naverMapKeyId").value("test-naver-key"))
			.andExpect(jsonPath("$.placeSearch").value(true));

		pushSender.setEnabled(false);
		mockMvc.perform(get("/api/config").with(loginAs(kim)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.push").value(nullValue()))
			.andExpect(jsonPath("$.naverMapKeyId").value("test-naver-key"));
	}

	record Device(Long userId, String fid, String sessionPrimaryId) {
	}

	private List<Device> devices() {
		return jdbcTemplate.query("select user_id, fid, session_primary_id from push_devices order by id",
				(rs, i) -> new Device(rs.getLong(1), rs.getString(2), rs.getString(3)));
	}

	private String primaryId(String sessionId) {
		return jdbcTemplate.queryForObject("select primary_id from spring_session where session_id = ?", String.class,
				sessionId);
	}
}
