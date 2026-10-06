package com.ohjumwhat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ohjumwhat.auth.LoginUser;
import com.ohjumwhat.user.User;

public final class TestAuth {

	private TestAuth() {
	}

	/** 해당 사용자로 구글 로그인한 상태의 요청을 만든다. */
	public static RequestPostProcessor loginAs(User user) {
		OidcIdToken idToken = OidcIdToken.withTokenValue("test-token")
			.subject(user.getGoogleSub())
			.claim("email", user.getEmail())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.build();
		DefaultOidcUser oidcUser = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken);
		return oidcLogin().oidcUser(new LoginUser(user.getId(), oidcUser));
	}

	/**
	 * 브라우저처럼 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더를 같은 값으로 보낸다.
	 * Spring Security의 csrf() 헬퍼는 공유 CsrfFilter의 저장소를 세션 방식으로 바꿔
	 * 이후 테스트의 쿠키 발급을 막으므로 쓰지 않는다.
	 */
	public static RequestPostProcessor xsrf() {
		return request -> {
			String token = UUID.randomUUID().toString();
			request.setCookies(new Cookie("XSRF-TOKEN", token));
			request.addHeader("X-XSRF-TOKEN", token);
			return request;
		};
	}

	/**
	 * xsrf()에 더해 그 로그인 세션의 SESSION 쿠키도 보낸다(세션 ID를 base64로, Spring Session 기본값).
	 * xsrf()는 쿠키를 통째로 바꾸므로 두 쿠키를 함께 만든다. 세션 행은 IntegrationTest.loginSession으로 만든다.
	 */
	public static RequestPostProcessor xsrf(String sessionId) {
		return request -> {
			String token = UUID.randomUUID().toString();
			request.setCookies(new Cookie("SESSION",
					Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8))),
					new Cookie("XSRF-TOKEN", token));
			request.addHeader("X-XSRF-TOKEN", token);
			return request;
		};
	}
}
