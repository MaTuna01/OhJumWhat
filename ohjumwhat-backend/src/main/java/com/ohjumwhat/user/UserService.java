package com.ohjumwhat.user;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ohjumwhat.organization.Membership;
import com.ohjumwhat.organization.MembershipRepository;

@Slf4j
@Service
public class UserService {

	private static final int NAME_MAX_LENGTH = 100;

	private final UserRepository userRepository;

	private final MembershipRepository membershipRepository;

	public UserService(UserRepository userRepository, MembershipRepository membershipRepository) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
	}

	@Transactional
	public User upsertGoogleUser(String googleSub, String email, String name, String profileImageUrl) {
		String displayName = displayName(name, email);
		return userRepository.findByGoogleSub(googleSub)
			.map(user -> {
				user.updateProfile(email, displayName, profileImageUrl);
				return user;
			})
			.orElseGet(() -> {
				User created = userRepository.save(new User(googleSub, email, displayName, profileImageUrl));
				log.info("신규 사용자 가입: userId={}", created.getId());
				return created;
			});
	}

	@Transactional(readOnly = true)
	public MeResponse getMe(Long userId) {
		User user = userRepository.findById(userId).orElseThrow();
		Long lastVisitedOrgId = membershipRepository.findFirstByUserIdOrderByLastVisitedAtDesc(userId)
			.map(Membership::getOrganizationId)
			.orElse(null);
		return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getProfileImageUrl(),
				lastVisitedOrgId);
	}

	/** 명단에 표시할 이름. 구글 이름이 없으면 이메일 앞부분을 쓴다. */
	private static String displayName(String name, String email) {
		String displayName = StringUtils.hasText(name) ? name.strip() : email.substring(0, email.indexOf('@'));
		return displayName.length() > NAME_MAX_LENGTH ? displayName.substring(0, NAME_MAX_LENGTH) : displayName;
	}
}
