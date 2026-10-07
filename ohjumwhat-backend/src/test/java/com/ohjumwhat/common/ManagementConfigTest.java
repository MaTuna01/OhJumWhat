package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * 운영 이미지는 application.example.yml을 그대로 쓰지만 테스트는 test/resources/application.yml만 읽는다.
 * 그래서 지표가 인터넷에 나가지 않게 하는 운영 설정(관리 포트·공개 범위)을 파일에서 직접 확인하고,
 * 테스트 설정이 포트 말고는 운영과 같은지 본다(그래야 ManagementEndpointsTest가 운영 설정을 시험한다).
 */
class ManagementConfigTest {

	@Test
	void 운영에서는_actuator를_관리_포트에서만_열고_공개는_healthz뿐이다() throws IOException {
		Map<String, String> production = management("src/main/resources/application.example.yml");

		assertThat(production).containsEntry("management.server.port", "8081")
			.containsEntry("management.endpoints.web.exposure.include", "health, prometheus")
			.containsEntry("management.endpoint.health.group.public.include", "db, diskSpace, readinessState")
			.containsEntry("management.endpoint.health.group.public.additional-path", "server:/healthz");
	}

	@Test
	void 테스트_설정은_포트_말고는_운영과_같다() throws IOException {
		Map<String, String> production = management("src/main/resources/application.example.yml");
		Map<String, String> test = management("src/test/resources/application.yml");

		assertThat(test).containsEntry("management.server.port", "0");
		production.remove("management.server.port");
		test.remove("management.server.port");
		assertThat(test).isEqualTo(production);
	}

	/** YAML 파일의 management.* 설정을 펼친 이름 → 값으로 읽는다. */
	private static Map<String, String> management(String path) throws IOException {
		Map<String, String> properties = new TreeMap<>();
		for (PropertySource<?> source : new YamlPropertySourceLoader().load(path, new FileSystemResource(path))) {
			EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
			for (String name : enumerable.getPropertyNames()) {
				if (name.startsWith("management.")) {
					properties.put(name, String.valueOf(enumerable.getProperty(name)));
				}
			}
		}
		return properties;
	}
}
