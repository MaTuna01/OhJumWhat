package com.ohjumwhat.auth;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import com.ohjumwhat.user.Role;
import com.ohjumwhat.user.UserRepository;

/**
 * /api/admin/**: 요청마다 DB의 회원 역할을 확인한다.
 * 세션(30일)에 권한을 넣어 두지 않으므로 관리자 지정·해제가 다시 로그인하지 않아도 바로 반영된다.
 */
@Component
public class AdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	private final UserRepository userRepository;

	public AdminAuthorizationManager(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		Authentication current = authentication.get();
		boolean admin = current != null && current.getPrincipal() instanceof LoginUser loginUser
				&& userRepository.existsByIdAndRole(loginUser.getUserId(), Role.ADMIN);
		return new AuthorizationDecision(admin);
	}
}
