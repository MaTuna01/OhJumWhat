package com.ohjumwhat.auth;

import java.io.Serial;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** 세션에 저장되는 로그인 사용자. 컨트롤러에서는 {@code @AuthenticationPrincipal LoginUser}로 받는다. */
public class LoginUser extends DefaultOidcUser {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long userId;

	public LoginUser(Long userId, OidcUser oidcUser) {
		super(oidcUser.getAuthorities(), oidcUser.getIdToken(), oidcUser.getUserInfo(), "sub");
		this.userId = userId;
	}

	/** users.id */
	public Long getUserId() {
		return userId;
	}
}
