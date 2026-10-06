package com.ohjumwhat.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 설정이 비었거나 잘못돼도 서버는 뜨고 푸시만 꺼진다. 설정 값은 로그에 남지 않는다. */
@ExtendWith(OutputCaptureExtension.class)
class PushSenderConfigurationTest {

	static final String PROJECT = "ohjumwhat-test";

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(PushProperties.class)
	@Import(PushSenderConfiguration.class)
	static class Config {
	}

	@Test
	void 설정이_없으면_꺼진다() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(PushSender.class).enabled()).isFalse();
		});
	}

	@Test
	void 하나라도_비면_꺼진다(CapturedOutput output) throws Exception {
		String serviceAccount = base64(serviceAccountJson(PROJECT));
		runner.withPropertyValues(web(PROJECT))
			.run(context -> assertThat(context.getBean(PushSender.class).enabled()).isFalse());
		runner.withPropertyValues(web(PROJECT))
			.withPropertyValues("ohjumwhat.push.web.vapid-key= ", "ohjumwhat.push.service-account=" + serviceAccount)
			.run(context -> assertThat(context.getBean(PushSender.class).enabled()).isFalse());

		assertThat(output).contains("비어 있는 키=[service-account]", "비어 있는 키=[web.vapid-key]")
			.doesNotContain(serviceAccount, "test-vapid-key");
	}

	@Test
	void 설정을_문자열로_찍어도_서비스_계정은_가린다() {
		PushProperties properties = new PushProperties(
				new PushProperties.Web("test-api-key", PROJECT, "test-app-id", "1234567890", "test-vapid-key"),
				"c2VjcmV0LXNlcnZpY2UtYWNjb3VudA==");

		assertThat(properties.toString()).doesNotContain("c2VjcmV0LXNlcnZpY2UtYWNjb3VudA==")
			.contains("serviceAccount=****", "test-api-key");
	}

	@Test
	void 서비스_계정을_읽지_못하면_꺼지고_서버는_뜬다(CapturedOutput output) throws Exception {
		String notJson = base64("{not json: 비밀-값-12345");
		String notServiceAccount = base64("""
				{"type": "authorized_user", "client_id": "비밀-값-12345", "client_secret": "s", "refresh_token": "r"}""");
		for (String serviceAccount : new String[] { "@@@ 비밀-값-12345 @@@", notJson, notServiceAccount }) {
			runner.withPropertyValues(web(PROJECT))
				.withPropertyValues("ohjumwhat.push.service-account=" + serviceAccount)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(PushSender.class).enabled()).isFalse();
				});
		}

		assertThat(output).contains("웹 푸시를 초기화하지 못해 푸시를 끕니다").doesNotContain("비밀-값-12345");
	}

	@Test
	void 서비스_계정의_프로젝트가_웹_설정과_다르면_꺼진다() throws Exception {
		runner.withPropertyValues(web(PROJECT))
			.withPropertyValues("ohjumwhat.push.service-account=" + base64(serviceAccountJson("other-project")))
			.run(context -> assertThat(context.getBean(PushSender.class).enabled()).isFalse());
	}

	@Test
	void 설정이_온전하면_Firebase로_보내고_서버가_멈추면_정리한다() throws Exception {
		// base64를 줄바꿈해 넣어도(GNU base64의 76자 줄바꿈) 읽는다.
		String serviceAccount = Base64.getMimeEncoder().encodeToString(
				serviceAccountJson(PROJECT).getBytes(StandardCharsets.UTF_8));
		runner.withPropertyValues(web(PROJECT))
			.withPropertyValues("ohjumwhat.push.service-account=" + serviceAccount)
			.run(context -> {
				PushSender sender = context.getBean(PushSender.class);
				assertThat(sender).isInstanceOf(FirebasePushSender.class);
				assertThat(sender.enabled()).isTrue();
				assertThat(FirebaseApp.getApps()).extracting(FirebaseApp::getName)
					.contains(FirebasePushSender.APP_NAME);
			});

		assertThat(FirebaseApp.getApps()).extracting(FirebaseApp::getName)
			.doesNotContain(FirebasePushSender.APP_NAME);
	}

	private static String[] web(String projectId) {
		return new String[] { "ohjumwhat.push.web.api-key=test-api-key", "ohjumwhat.push.web.project-id=" + projectId,
				"ohjumwhat.push.web.app-id=test-app-id", "ohjumwhat.push.web.messaging-sender-id=1234567890",
				"ohjumwhat.push.web.vapid-key=test-vapid-key" };
	}

	private static String base64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	/** 형식만 맞는 서비스 계정 JSON(새로 만든 RSA 키). 실제 Google 계정이 아니라 보내면 인증에 실패한다. */
	private static String serviceAccountJson(String projectId) throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
					.encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		return """
				{"type": "service_account", "project_id": "%s", "private_key_id": "test-key-id",
				 "private_key": "%s", "client_email": "push@%s.iam.gserviceaccount.com", "client_id": "1234567890",
				 "token_uri": "https://oauth2.googleapis.com/token"}"""
			.formatted(projectId, pem.replace("\n", "\\n"), projectId);
	}
}
