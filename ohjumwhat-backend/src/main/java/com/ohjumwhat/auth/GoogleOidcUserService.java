package com.ohjumwhat.auth;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserService;

/** 구글 로그인에 성공하면 google_sub 기준으로 users를 만들거나 프로필을 갱신한다. */
@Slf4j
@Component
public class GoogleOidcUserService extends OidcUserService {

	private final UserService userService;

	public GoogleOidcUserService(UserService userService) {
		this.userService = userService;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) {
		OidcUser oidcUser = super.loadUser(userRequest);
		User user = userService.upsertGoogleUser(oidcUser.getSubject(), oidcUser.getEmail(), oidcUser.getFullName(),
				oidcUser.getPicture());
		log.info("구글 로그인 성공: userId={}", user.getId());
		return new LoginUser(user.getId(), oidcUser);
	}
}
