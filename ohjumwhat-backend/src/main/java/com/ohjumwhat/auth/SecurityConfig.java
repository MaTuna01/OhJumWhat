package com.ohjumwhat.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, GoogleOidcUserService googleOidcUserService) {
		http
			// 화면 경로는 SPA가 처리하고, 로그인 여부는 /api 호출의 401로 판단한다.
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll())
			// 로그인 후에는 항상 /로 보내므로 요청을 세션에 저장하지 않는다. (익명 요청마다 세션이 생기는 것도 막는다)
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
				new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
				PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
			// /login은 SPA 화면이다. 로그인 버튼은 /oauth2/authorization/google로 이동한다.
			// 로그인 후에는 항상 /로 보내고, 어디로 갈지(초대 링크·최근 조직·마이페이지)는 프론트가 정한다.
			.oauth2Login(login -> login
				.loginPage("/login")
				.userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
				.defaultSuccessUrl("/", true)
				.failureUrl("/login?error"))
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// XSRF-TOKEN 쿠키를 프론트가 읽어 X-XSRF-TOKEN 헤더로 돌려보낸다.
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
		return http.build();
	}
}
