package com.ohjumwhat.sanction;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.common.ApiException;

/**
 * {@link Restricted} 핸들러를 부르기 전에 제재를 확인한다. 요청 본문 검사(@Valid)·멤버 확인·속도 제한·바깥 호출(카카오, naver.me)·
 * 사진 파일 쓰기보다 먼저라, 막힌 요청은 아무것도 하지 않고 423으로 끝난다(ApiException → GlobalExceptionHandler).
 * 로그인하지 않은 요청은 SecurityConfig가 먼저 401로 막는다.
 */
@Component
class SanctionInterceptor implements HandlerInterceptor {

	private final SanctionGuard guard;

	SanctionInterceptor(SanctionGuard guard) {
		this.guard = guard;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		Restricted restricted = method.getMethodAnnotation(Restricted.class);
		if (restricted == null) {
			return true;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			// 지금은 /api/**가 모두 로그인을 요구해 여기까지 오지 않는다. 나중에 로그인 없이 여는 쓰기 API가 생겨도 막는 쪽으로 둔다.
			throw ApiException.unauthorized("로그인이 필요해요.");
		}
		guard.check(loginUser.getUserId(), restricted.value());
		return true;
	}
}
