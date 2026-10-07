package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.ohjumwhat.FakeKakaoLocalConfiguration;
import com.ohjumwhat.FakeNaverShortLinksConfiguration;
import com.ohjumwhat.TestClockConfiguration;
import com.ohjumwhat.TestcontainersConfiguration;
import com.ohjumwhat.user.UserRepository;

/**
 * 서버 모니터링용 actuator 구성을 실제 서버 포트로 확인한다.
 * 공개 헬스 체크는 앱 포트의 /healthz(상태만)이고, 지표는 Caddy가 프록시하지 않는 관리 포트에서만 나간다.
 * 테스트는 지표 내보내기를 끄므로(Spring Boot 기본) @AutoConfigureMetrics로 운영처럼 Prometheus 지표를 켠다.
 * 그래서 ChatSocketTest·PwaFileHeadersTest와 서버(컨텍스트)를 함께 쓰지 못하고 따로 띄운다.
 * 테스트 설정이 운영 설정과 같은지는 ManagementConfigTest가 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeNaverShortLinksConfiguration.class,
		FakeKakaoLocalConfiguration.class })
@AutoConfigureMetrics
class ManagementEndpointsTest {

	@LocalServerPort
	int port;

	@LocalManagementPort
	int managementPort;

	@Autowired
	UserRepository userRepository;

	final HttpClient client = HttpClient.newHttpClient();

	@Test
	void 공개_헬스_체크는_앱_포트의_healthz에서_상태만_준다() throws Exception {
		HttpResponse<String> response = get(port, "/healthz");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "details");
	}

	@Test
	void 앱_포트의_actuator_경로는_없는_화면이다() throws Exception {
		assertThat(managementPort).isNotEqualTo(port);
		for (String path : new String[] { "/actuator/prometheus", "/actuator/health", "/actuator" }) {
			HttpResponse<String> response = get(port, path);
			// 파일이 없는 화면 경로라 SPA가 index.html(테스트용 spa-index)을 준다.
			assertThat(response.statusCode()).as(path).isEqualTo(200);
			assertThat(response.body()).as(path).contains("spa-index").doesNotContain("jvm_", "\"status\"");
		}
	}

	@Test
	void 관리_포트는_요청_JVM_DB_채팅_지표를_준다() throws Exception {
		get(port, "/healthz");
		userRepository.count();

		HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("http_server_requests_seconds_count", "jvm_memory_used_bytes",
				"hikaricp_connections_active", "ohjumwhat_chat_connections")
			// 저장소 호출 지표는 시계열만 늘려서 끈다.
			.doesNotContain("spring_data_repository");
	}

	private HttpResponse<String> get(int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
