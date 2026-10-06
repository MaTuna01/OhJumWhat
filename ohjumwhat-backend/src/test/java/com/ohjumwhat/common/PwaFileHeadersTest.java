package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.ohjumwhat.FakeKakaoLocalConfiguration;
import com.ohjumwhat.FakeNaverShortLinksConfiguration;
import com.ohjumwhat.TestClockConfiguration;
import com.ohjumwhat.TestcontainersConfiguration;

/**
 * 웹 푸시 서비스 워커와 PWA 매니페스트를 실제 서버(Tomcat)가 어떤 헤더로 내보내는지 본다(테스트용 파일은 src/test/resources/static).
 * 배포하면 바로 새 파일을 받도록 no-cache이고, 매니페스트는 manifest 형식이다. ChatSocketTest와 같은 설정이라 서버를 함께 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "server.forward-headers-strategy=framework")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeNaverShortLinksConfiguration.class,
		FakeKakaoLocalConfiguration.class })
class PwaFileHeadersTest {

	@LocalServerPort
	int port;

	final HttpClient client = HttpClient.newHttpClient();

	@Test
	void 서비스_워커는_no_cache인_자바스크립트다() throws Exception {
		HttpResponse<String> response = get("/firebase-messaging-sw.js");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-cache");
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("text/javascript"));
	}

	@Test
	void 매니페스트는_no_cache인_manifest_형식이다() throws Exception {
		HttpResponse<String> response = get("/manifest.webmanifest");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-cache");
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/manifest+json"));
		assertThat(response.body()).contains("오점왓");
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
