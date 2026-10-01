package com.ohjumwhat.auth;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.ohjumwhat.user.BlockedAccountException;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserService;

/** 구글 로그인에 성공하면 google_sub 기준으로 users를 만들거나 프로필을 갱신한다. 차단된 계정은 거절한다. */
@Slf4j
@Component
public class GoogleOidcUserService extends OidcUserService {

	/** 로그인 실패 핸들러가 이 코드를 보고 /login?error=blocked로 보낸다. */
	static final String ACCOUNT_BLOCKED = "account_blocked";

	private final UserService userService;

	public GoogleOidcUserService(UserService userService) {
		this.userService = userService;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) {
		OidcUser oidcUser = super.loadUser(userRequest);
		try {
			User user = userService.login(oidcUser.getSubject(), oidcUser.getEmail(),
					Boolean.TRUE.equals(oidcUser.getEmailVerified()), oidcUser.getFullName(), oidcUser.getPicture());
			log.info("구글 로그인 성공: userId={}", user.getId());
			return new LoginUser(user.getId(), oidcUser);
		}
		catch (BlockedAccountException e) {
			// OAuth2AuthenticationException이어야 로그인 실패로 처리된다(다른 예외는 500이 된다).
			log.info("차단된 계정의 로그인 거절: blockId={}", e.getBlockId());
			throw new OAuth2AuthenticationException(new OAuth2Error(ACCOUNT_BLOCKED), e.getMessage());
		}
	}
}
