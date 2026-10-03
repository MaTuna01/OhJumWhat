package com.ohjumwhat.auth;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpSession;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.ohjumwhat.chat.ChatHub;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, GoogleOidcUserService googleOidcUserService,
			AdminAuthorizationManager adminAuthorizationManager, ChatHub chatHub) {
		http
			// 화면 경로는 SPA가 처리하고, 로그인 여부는 /api 호출의 401로 판단한다.
			// 관리자 API는 요청마다 DB의 역할을 확인한다(관리자가 아니면 403).
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/admin/**").access(adminAuthorizationManager)
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll())
			// 로그인 후에는 항상 /로 보내므로 요청을 세션에 저장하지 않는다. (익명 요청마다 세션이 생기는 것도 막는다)
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.exceptionHandling(ex -> ex
				.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api())
				.defaultAccessDeniedHandlerFor(jsonAccessDenied(), api()))
			// /login은 SPA 화면이다. 로그인 버튼은 /oauth2/authorization/google로 이동한다.
			// 로그인 후에는 항상 /로 보내고, 어디로 갈지(초대 링크·최근 조직·마이페이지)는 프론트가 정한다.
			.oauth2Login(login -> login
				.loginPage("/login")
				.userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
				.defaultSuccessUrl("/", true)
				.failureHandler(loginFailure()))
			// 로그아웃하면 이 로그인(세션)으로 연 채팅 연결도 끊는다(세션을 지우기 전에 실행된다).
			.logout(logout -> logout
				.logoutUrl("/logout")
				.addLogoutHandler(closeChatConnections(chatHub))
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// XSRF-TOKEN 쿠키를 프론트가 읽어 X-XSRF-TOKEN 헤더로 돌려보낸다.
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
		return http.build();
	}

	private static LogoutHandler closeChatConnections(ChatHub chatHub) {
		return (request, response, authentication) -> {
			HttpSession session = request.getSession(false);
			if (session != null) {
				chatHub.closeHttpSession(session.getId());
			}
		};
	}

	private static PathPatternRequestMatcher api() {
		return PathPatternRequestMatcher.withDefaults().matcher("/api/**");
	}

	/** API의 403도 다른 오류처럼 {"message": "..."}로 준다. 프론트 api()가 이 메시지를 보여준다. */
	private static AccessDeniedHandler jsonAccessDenied() {
		return (request, response, e) -> {
			String message = e instanceof CsrfException ? "보안 토큰이 만료됐어요. 새로고침한 뒤 다시 시도해 주세요." : "권한이 없어요.";
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("{\"message\":\"" + message + "\"}");
		};
	}

	/** 차단된 계정은 /login?error=blocked, 그 밖의 실패는 /login?error로 보낸다(예외를 세션에 남기지 않는다). */
	static AuthenticationFailureHandler loginFailure() {
		RedirectStrategy redirect = new DefaultRedirectStrategy();
		return (request, response, e) -> {
			boolean blocked = e instanceof OAuth2AuthenticationException oauth2
					&& GoogleOidcUserService.ACCOUNT_BLOCKED.equals(oauth2.getError().getErrorCode());
			redirect.sendRedirect(request, response, blocked ? "/login?error=blocked" : "/login?error");
		};
	}
}
