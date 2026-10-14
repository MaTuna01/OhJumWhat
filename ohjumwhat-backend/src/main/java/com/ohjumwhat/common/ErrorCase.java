package com.ohjumwhat.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 명세(Swagger)에 적을 오류 응답 한 가지. 응답 본문은 {@code {"message": message}}다(GlobalExceptionHandler).
 * 같은 코드를 여러 번 달면 예시가 여러 개가 된다. 문서에만 쓰고 동작은 바꾸지 않는다.
 * 로그인(401)·관리자 권한(403)·CSRF(403)·제재(423)·요청 값 검사(400)·사진 올리기(400·413)는 OpenApiConfig가 저절로 붙이므로
 * 여기에는 서비스가 던지는 오류(ApiException)만 적는다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ErrorCase.List.class)
public @interface ErrorCase {

	/** 응답 코드(400, 403, 404, 409, 429, 503 등) */
	int status();

	/** 응답의 message 그대로(서비스의 ApiException 문구) */
	String message();

	/** 언제 나는지. 예시 이름으로 보인다. 비우면 message를 쓴다. */
	String when() default "";

	@Documented
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface List {

		ErrorCase[] value();
	}
}
