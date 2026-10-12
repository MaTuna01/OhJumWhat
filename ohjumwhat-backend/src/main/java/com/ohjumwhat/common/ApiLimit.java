package com.ohjumwhat.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 명세(Swagger)에 적을 실제 한도: 글자면 maxLength(코드 포인트 수), 목록이면 maxItems.
 * 요청 필드의 {@code @Size}가 공백 정리 전 원문을 막는 넉넉한 상한이고 진짜 한도는 서비스가 정리한 뒤 확인할 때 단다
 * (예: 채팅 본문 {@code @Size(max = 2000)}, 실제 300자). 문서에만 쓰고 동작은 바꾸지 않는다(OpenApiConfig).
 */
@Documented
// record 구성 요소에 달면 필드와 접근자 메서드로 퍼진다(swagger-core는 접근자로 읽는다).
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiLimit {

	int value();

	/** 목록 원소(글자)의 실제 한도. 0이면 적지 않는다. */
	int items() default 0;
}
