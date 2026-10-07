package com.ohjumwhat.sanction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API는 그 기능을 막은 제재(또는 활동 정지)가 걸린 회원에게 423으로 답한다(SanctionInterceptor, 컨트롤러보다 먼저).
 * /api/**(관리자 API 제외)의 GET이 아닌 핸들러는 모두 이것이나 {@link Unrestricted} 중 하나를 단다(SanctionCoverageTest).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Restricted {

	Restriction value();
}
