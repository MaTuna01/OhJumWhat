package com.ohjumwhat.sanction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API는 어떤 제재로도 막지 않는다(투표 참여·취소, 지우기, 읽음, 신고·차단, 조직 탈퇴, 알림 기기, 안내 확인 등).
 * 새 쓰기 API를 만들 때 막을지 정했다는 표시다(SanctionCoverageTest가 빠뜨린 핸들러를 잡는다).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Unrestricted {
}
