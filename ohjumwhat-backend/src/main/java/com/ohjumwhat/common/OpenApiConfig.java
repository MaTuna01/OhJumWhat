package com.ohjumwhat.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import jakarta.validation.Valid;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.ohjumwhat.sanction.Restricted;
import com.ohjumwhat.sanction.Restriction;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API 명세(Swagger UI {@code /swagger-ui.html}, OpenAPI {@code /v3/api-docs/user}·{@code /admin}). 로컬·개발에서만 켠다
 * ({@code springdoc.api-docs.enabled} = {@code SWAGGER_ENABLED}, 운영은 꺼져 이 설정도 만들지 않는다).
 * <p>
 * 오류 응답은 API마다 손으로 적지 않아도 되게 여기서 붙인다: 로그인(401)·관리자 권한(403)·CSRF(403)는 경로와 메서드로,
 * 제재(423)는 {@link Restricted}로, 요청 값 검사(400)는 {@code @Valid} 요청 본문의 검증 문구로, 사진 올리기(400·413)는
 * {@link MultipartFile} 파라미터로. 서비스가 던지는 오류는 핸들러의 {@link ErrorCase}로 적는다.
 */
@Configuration
// springdoc도 설정이 없으면 켜지므로 같게 둔다(운영 이미지는 application.example.yml의 false를 쓴다).
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
class OpenApiConfig {

	private static final String ERROR_SCHEMA = "ErrorResponse";

	private static final String SESSION = "session";

	/** {@link ApiLimit} 표시(명세에는 남기지 않는다) */
	private static final String LIMIT = "x-api-limit";

	/** 제재 423 예시의 끝나는 시각(SanctionGuard.until과 같은 모양) */
	private static final String UNTIL_EXAMPLE = "10월 14일 오후 6:00까지";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("오점왓 API").version(version()).description(overview()))
			.tags(ApiTags.ALL)
			.components(new Components()
				.addSecuritySchemes(SESSION, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.COOKIE)
					.name("SESSION")
					.description("구글 로그인 뒤 서버가 내리는 세션 쿠키(HttpOnly, 30일). 브라우저가 저절로 보낸다."))
				.addSchemas(ERROR_SCHEMA, new ObjectSchema().description("오류 응답")
					.addProperty("message", new StringSchema().description("사용자에게 그대로 보여줄 문구")
						.example("투표를 찾을 수 없어요."))));
	}

	@Bean
	GroupedOpenApi userApi() {
		return GroupedOpenApi.builder()
			.group("user")
			.displayName("사용자 화면 API")
			.pathsToMatch("/api/**")
			.pathsToExclude("/api/admin/**")
			.build();
	}

	@Bean
	GroupedOpenApi adminApi() {
		return GroupedOpenApi.builder()
			.group("admin")
			.displayName("관리자 콘솔 API")
			.pathsToMatch("/api/admin/**")
			.build();
	}

	/** 핸들러에서 알 수 있는 오류: {@link ErrorCase}, 제재(423), 요청 값 검사(400), 사진 올리기(400·413) */
	@Bean
	GlobalOperationCustomizer handlerErrors() {
		return (operation, handler) -> {
			for (ErrorCase error : handler.getMethod().getAnnotationsByType(ErrorCase.class)) {
				addError(operation, error.status(), error.when(), error.message());
			}
			Restricted restricted = handler.getMethodAnnotation(Restricted.class);
			if (restricted != null) {
				addError(operation, 423, "관리자가 이 기능을 제한했을 때(요청 값 검사보다 먼저)",
						restricted.value().message(UNTIL_EXAMPLE));
				if (restricted.value() != Restriction.SUSPEND) {
					addError(operation, 423, "활동 정지일 때", Restriction.SUSPEND.message("해제될 때까지"));
				}
			}
			for (MethodParameter parameter : handler.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(RequestBody.class)) {
					addError(operation, 400, "본문이 JSON이 아니거나 형식이 틀렸을 때", "요청 형식이 올바르지 않아요.");
					if (parameter.hasParameterAnnotation(Valid.class)
							|| parameter.hasParameterAnnotation(Validated.class)) {
						validationMessages(parameter.getParameterType())
							.forEach(message -> addError(operation, 400, "", message));
					}
				}
				if (parameter.getParameterType() == MultipartFile.class
						|| parameter.hasParameterAnnotation(RequestPart.class)) {
					addError(operation, 400, "multipart가 아니거나 파일 파트가 없을 때", "사진 파일을 보내 주세요.");
					addError(operation, 413, "파일이 업로드 한도(5MB)를 넘을 때", "사진이 너무 커요.");
				}
			}
			return operation;
		};
	}

	/**
	 * 요청 필드의 {@link ApiLimit}(진짜 한도)가 넉넉한 {@code @Size} 상한을 이긴다. swagger-core는 이 customizer 뒤에
	 * {@code @Size}를 적용하므로 여기서는 표시({@value #LIMIT})만 남기고 {@link #schemaCleanup()}이 값을 넣는다.
	 */
	@Bean
	PropertyCustomizer apiLimits() {
		return (property, type) -> {
			if (type.getCtxAnnotations() == null) {
				return property;
			}
			for (Annotation annotation : type.getCtxAnnotations()) {
				if (annotation instanceof ApiLimit limit) {
					property.addExtension(LIMIT, List.of(limit.value(), limit.items()));
				}
			}
			return property;
		};
	}

	/**
	 * 스키마 정리. null일 수 있는 객체 필드는 swagger-core가 {@code {"type": "null", "$ref": …}}로 내는데, OpenAPI 3.1(JSON Schema
	 * 2020-12)에서는 $ref 옆의 키도 함께 검사해 맞는 값이 없다. {@code anyOf: [$ref, null]}로 바꾼다. {@code @Size}의 쓸모없는
	 * 하한 0도 뺀다.
	 */
	@Bean
	GlobalOpenApiCustomizer schemaCleanup() {
		return openApi -> {
			if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
				return;
			}
			for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
				Map<String, Schema> properties = schema.getProperties();
				if (properties == null) {
					continue;
				}
				properties.replaceAll((name, property) -> cleanProperty(property));
			}
		};
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Schema cleanProperty(Schema property) {
		if (property.getExtensions() != null && property.getExtensions().remove(LIMIT) instanceof List<?> limit) {
			int value = (Integer) limit.get(0);
			int items = (Integer) limit.get(1);
			if (property.getItems() != null) {
				property.setMaxItems(value);
				if (items > 0) {
					property.getItems().setMaxLength(items);
				}
			}
			else {
				property.setMaxLength(value);
			}
			if (property.getExtensions().isEmpty()) {
				property.setExtensions(null);
			}
		}
		Schema items = property.getItems();
		if (items != null) {
			if (Integer.valueOf(0).equals(items.getMinLength())) {
				items.setMinLength(null);
			}
			// 원소 타입에도 같은 어노테이션이 보여 표시가 붙는다(값은 위에서 목록이 넣었다).
			if (items.getExtensions() != null) {
				items.getExtensions().remove(LIMIT);
				if (items.getExtensions().isEmpty()) {
					items.setExtensions(null);
				}
			}
		}
		if (Integer.valueOf(0).equals(property.getMinLength())) {
			property.setMinLength(null);
		}
		if (Integer.valueOf(0).equals(property.getMinItems())) {
			property.setMinItems(null);
		}
		boolean nullableRef = property.get$ref() != null
				&& (Boolean.TRUE.equals(property.getNullable())
						|| property.getTypes() != null && property.getTypes().contains("null"));
		if (!nullableRef) {
			return property;
		}
		Schema nullType = new Schema();
		nullType.setTypes(Set.of("null"));
		Schema wrapped = new Schema().anyOf(List.of(new Schema().$ref(property.get$ref()), nullType));
		wrapped.setDescription(property.getDescription());
		return wrapped;
	}

	/** 경로·메서드로 정해지는 오류(401·403)를 붙이고 응답 코드 순서로 정렬한다. 그 묶음(사용자·관리자)에 없는 태그는 뺀다. */
	@Bean
	GlobalOpenApiCustomizer pathErrors() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
				operation.addSecurityItem(new SecurityRequirement().addList(SESSION));
				if (method != PathItem.HttpMethod.GET) {
					addError(operation, 403, "CSRF: X-XSRF-TOKEN 헤더가 없거나 XSRF-TOKEN 쿠키와 다를 때",
							"보안 토큰이 만료됐어요. 새로고침한 뒤 다시 시도해 주세요.");
				}
				if (path.startsWith("/api/admin/")) {
					addError(operation, 403, "관리자가 아닐 때(요청마다 DB의 역할로 확인)", "권한이 없어요.");
				}
				ApiResponses responses = operation.getResponses();
				responses.computeIfAbsent("401", code -> new ApiResponse()
					.description("로그인하지 않았어요(세션 쿠키가 없거나 만료). **본문 없음**. 화면은 로그인 화면으로 보낸다. "
							+ "세션은 있는데 회원이 없어진 경우(강제 탈퇴 직후)에는 {\"message\": \"다시 로그인해 주세요.\"}가 올 수 있다."));
				Map<String, ApiResponse> sorted = new TreeMap<>(responses);
				responses.clear();
				responses.putAll(sorted);
			}));
			Set<String> used = new HashSet<>();
			openApi.getPaths()
				.values()
				.forEach(item -> item.readOperations()
					.forEach(operation -> used.addAll(Objects.requireNonNullElse(operation.getTags(), List.of()))));
			openApi.setTags(new ArrayList<>(ApiTags.ALL.stream().filter(tag -> used.contains(tag.getName())).toList()));
		};
	}

	private static void addError(Operation operation, int status, String when, String message) {
		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			responses = new ApiResponses();
			operation.setResponses(responses);
		}
		ApiResponse response = responses.computeIfAbsent(String.valueOf(status),
				code -> new ApiResponse().description(statusText(status)));
		if (response.getContent() == null) {
			response.setContent(new Content());
		}
		MediaType json = response.getContent()
			.computeIfAbsent(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
					type -> new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)));
		if (json.getExamples() == null) {
			json.setExamples(new LinkedHashMap<>());
		}
		boolean known = json.getExamples().values().stream().anyMatch(example -> example.getValue() instanceof Map<?, ?> value
				&& message.equals(value.get("message")));
		if (known) {
			return;
		}
		String name = when.isBlank() ? message : when;
		json.getExamples().put(name, new Example().summary(name).value(Map.of("message", message)));
		if (!when.isBlank()) {
			response.setDescription(response.getDescription() + "\n- " + when);
		}
	}

	private static String statusText(int status) {
		return switch (status) {
			case 400 -> "요청 값이 올바르지 않아요.";
			case 401 -> "로그인하지 않았어요.";
			case 403 -> "권한이 없어요.";
			case 404 -> "찾을 수 없어요. 조직 멤버가 아니어도 404다(조직이 있는지 알리지 않는다).";
			case 409 -> "지금 상태에서는 할 수 없어요(마감됨, 중복, 다른 요청과 겹침 등).";
			case 413 -> "파일이 너무 커요.";
			case 423 -> "관리자의 이용 제한(제재)으로 막혔어요. 문구에 끝나는 시각이 들어 있다.";
			case 429 -> "너무 자주 요청했어요.";
			case 503 -> "바깥 서비스(지도 검색 등)나 서버가 잠시 바빠요.";
			default -> "오류";
		};
	}

	/** 요청 본문 record·클래스 필드의 jakarta.validation 문구(기본 문구 {…}는 뺀다) */
	private static List<String> validationMessages(Class<?> type) {
		List<String> messages = new ArrayList<>();
		for (Field field : type.getDeclaredFields()) {
			for (Annotation annotation : field.getAnnotations()) {
				if (!annotation.annotationType().getPackageName().equals("jakarta.validation.constraints")) {
					continue;
				}
				String message = constraintMessage(annotation);
				if (message != null && !message.startsWith("{") && !messages.contains(message)) {
					messages.add(message);
				}
			}
		}
		return messages;
	}

	private static String constraintMessage(Annotation annotation) {
		try {
			Method message = annotation.annotationType().getMethod("message");
			return (String) message.invoke(annotation);
		}
		catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static String version() {
		String version = OpenApiConfig.class.getPackage().getImplementationVersion();
		return version != null ? version : "dev";
	}

	private static String overview() {
		try {
			return new ClassPathResource("apidoc/overview.md").getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
