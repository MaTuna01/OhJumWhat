package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.ohjumwhat.IntegrationTest;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.tags.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API 명세(Swagger)가 만들어지고, /api/**의 API가 하나도 빠지지 않고 요약·묶음·파라미터 설명을 갖췄는지 본다.
 * 새 API를 만들면 {@code @Operation(summary)}와 컨트롤러의 {@code @Tag(name = ApiTags.…)}, 경로·쿼리 파라미터의
 * {@code @Parameter(description)}를 단다. 만든 명세는 build/api-docs/에 남긴다(눈으로 확인용).
 */
class ApiDocsTest extends IntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	void 모든_API가_명세에_있고_요약과_묶음과_파라미터_설명이_있다() throws Exception {
		JsonNode user = docs("user");
		JsonNode admin = docs("admin");
		Set<String> tagNames = new HashSet<>(ApiTags.ALL.stream().map(Tag::getName).toList());

		Set<String> documented = new HashSet<>();
		List<String> problems = new ArrayList<>();
		for (JsonNode group : List.of(user, admin)) {
			for (Map.Entry<String, JsonNode> path : group.get("paths").properties()) {
				for (Map.Entry<String, JsonNode> method : path.getValue().properties()) {
					String name = method.getKey().toUpperCase() + " " + path.getKey();
					documented.add(name);
					JsonNode operation = method.getValue();
					if (operation.path("summary").asString("").isBlank()) {
						problems.add(name + ": summary 없음");
					}
					JsonNode tags = operation.path("tags");
					if (tags.size() != 1 || !tagNames.contains(tags.get(0).asString())) {
						problems.add(name + ": ApiTags의 묶음 하나가 아님 " + tags);
					}
					for (JsonNode parameter : operation.path("parameters")) {
						if (parameter.path("description").asString("").isBlank()) {
							problems.add(name + ": 파라미터 " + parameter.path("name").asString() + " 설명 없음");
						}
					}
					if (!operation.path("responses").has("401")) {
						problems.add(name + ": 401 없음");
					}
				}
			}
		}

		List<String> missing = new ArrayList<>();
		for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
			for (String pattern : info.getPatternValues()) {
				if (!pattern.startsWith("/api/")) {
					continue;
				}
				for (RequestMethod method : info.getMethodsCondition().getMethods()) {
					// 정규식 경로 변수({key:[0-9a-f]+})는 명세에 {key}로 나온다.
					String name = method.name() + " " + pattern.replaceAll("\\{(\\w+):[^}]*}", "{$1}");
					if (!documented.contains(name)) {
						missing.add(name);
					}
				}
			}
		}
		assertThat(missing).as("명세에 없는 API").isEmpty();
		assertThat(problems).as("명세가 덜 된 API").isEmpty();
	}

	/**
	 * 명세의 스키마 이름은 클래스의 짧은 이름이라, 다른 클래스가 같은 이름이면(중첩 record Item·Entry 등) 하나가 다른 하나를 덮어쓴다.
	 * 겹치면 {@code @Schema(name = "…")}으로 이름을 정한다.
	 */
	@Test
	void 요청_응답_스키마_이름이_겹치지_않는다() {
		Map<String, Set<Class<?>>> byName = new HashMap<>();
		Set<Class<?>> seen = new HashSet<>();
		handlerMapping.getHandlerMethods().forEach((info, handler) -> {
			if (info.getPatternValues().stream().noneMatch(p -> p.startsWith("/api/"))) {
				return;
			}
			collect(handler.getMethod().getGenericReturnType(), seen, byName);
			for (MethodParameter parameter : handler.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(RequestBody.class)) {
					collect(parameter.getGenericParameterType(), seen, byName);
				}
			}
		});
		Map<String, Set<Class<?>>> duplicates = new TreeMap<>();
		byName.forEach((name, classes) -> {
			if (classes.size() > 1) {
				duplicates.put(name, classes);
			}
		});
		assertThat(duplicates).as("@Schema(name)으로 이름을 나눠야 하는 스키마").isEmpty();
	}

	private static void collect(Type type, Set<Class<?>> seen, Map<String, Set<Class<?>>> byName) {
		if (type instanceof ParameterizedType parameterized) {
			collect(parameterized.getRawType(), seen, byName);
			for (Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, seen, byName);
			}
			return;
		}
		if (!(type instanceof Class<?> cls) || !cls.getName().startsWith("com.ohjumwhat.") || !seen.add(cls)) {
			return;
		}
		Schema schema = cls.getAnnotation(Schema.class);
		String name = schema != null && !schema.name().isBlank() ? schema.name() : cls.getSimpleName();
		byName.computeIfAbsent(name, key -> new HashSet<>()).add(cls);
		if (cls.isRecord()) {
			for (RecordComponent component : cls.getRecordComponents()) {
				collect(component.getGenericType(), seen, byName);
			}
		}
	}

	@Test
	void 오류_응답은_공통_형식과_예시를_갖는다() throws Exception {
		JsonNode vote = docs("user").at("/paths/~1api~1polls~1{pollId}~1vote/put/responses");

		assertThat(vote.has("401")).isTrue();
		assertThat(vote.at("/403/content/application~1json/examples").toString()).contains("보안 토큰이 만료됐어요.");
		assertThat(vote.at("/404/content/application~1json/schema/$ref").asString())
			.isEqualTo("#/components/schemas/ErrorResponse");
		assertThat(vote.at("/409/content/application~1json/examples").toString()).contains("마감된 투표예요.");
		// 투표 참여는 어떤 제재로도 막지 않는다.
		assertThat(vote.has("423")).isFalse();

		JsonNode addOption = docs("user").at("/paths/~1api~1polls~1{pollId}~1options/post/responses");
		assertThat(addOption.at("/423/content/application~1json/examples").toString())
			.contains("관리자가 메뉴 올리기·댓글·투표 관리를 제한했어요(10월 14일 오후 6:00까지).");
		assertThat(addOption.at("/400/content/application~1json/examples").toString())
			.contains("메뉴 이름을 입력해 주세요.", "요청 형식이 올바르지 않아요.");
	}

	private JsonNode docs(String group) throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs/" + group))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		Path out = Path.of("build", "api-docs", group + ".json");
		Files.createDirectories(out.getParent());
		Files.writeString(out, body);
		return JSON.readTree(body);
	}
}
