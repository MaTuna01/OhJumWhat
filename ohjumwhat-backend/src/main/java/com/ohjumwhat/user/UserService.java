package com.ohjumwhat.user;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ohjumwhat.admin.AdminProperties;
import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.organization.Membership;
import com.ohjumwhat.organization.MembershipRepository;

@Slf4j
@Service
public class UserService {

	private static final int NAME_MAX_LENGTH = 100;

	private final UserRepository userRepository;

	private final MembershipRepository membershipRepository;

	private final BlockedAccountRepository blockedAccountRepository;

	private final AdminProperties adminProperties;

	private final Clock clock;

	public UserService(UserRepository userRepository, MembershipRepository membershipRepository,
			BlockedAccountRepository blockedAccountRepository, AdminProperties adminProperties, Clock clock) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.blockedAccountRepository = blockedAccountRepository;
		this.adminProperties = adminProperties;
		this.clock = clock;
	}

	/**
	 * 구글 로그인. 차단된 계정이면 거절하고, 아니면 가입·프로필 갱신 후 최근 로그인 시각을 남긴다.
	 * 관리자 이메일이고 구글이 이메일을 인증했으면 관리자로 지정한다.
	 */
	@Transactional
	public User login(String googleSub, String email, boolean emailVerified, String name, String profileImageUrl) {
		requireNotBlocked(googleSub);
		User user = upsertGoogleUser(googleSub, email, name, profileImageUrl);
		user.recordLogin(Instant.now(clock));
		if (emailVerified && !user.isAdmin() && adminProperties.isAdminEmail(email)) {
			user.promote();
			log.info("관리자로 지정(로그인): userId={}", user.getId());
		}
		// 강제 탈퇴가 이 로그인과 동시에 커밋됐다면 지금은 차단 행이 보인다. 예외로 재가입을 되돌린다.
		userRepository.flush();
		requireNotBlocked(googleSub);
		return user;
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

	/**
	 * 관리자 목록을 설정(ohjumwhat.admin.emails)과 맞춘다: 목록의 이메일은 관리자로, 목록에 없는 관리자는 일반 회원으로.
	 * 설정이 비어 있으면 실수로 모든 관리자를 해제하지 않도록 아무것도 바꾸지 않는다.
	 */
	@Transactional
	public void syncAdmins() {
		List<String> emails = adminProperties.emails();
		if (emails.isEmpty()) {
			log.warn("관리자 이메일(ADMIN_EMAILS)이 설정되지 않아 관리자 목록을 바꾸지 않습니다.");
			return;
		}
		for (User user : userRepository.findByEmailIn(emails)) {
			if (!user.isAdmin()) {
				user.promote();
				log.info("관리자로 지정(설정): userId={}", user.getId());
			}
		}
		for (User admin : userRepository.findByRole(Role.ADMIN)) {
			if (!adminProperties.isAdminEmail(admin.getEmail())) {
				admin.demote();
				log.info("관리자 해제(설정에 없음): userId={}", admin.getId());
			}
		}
	}

	/** 강제 탈퇴 등으로 회원이 없어졌다면 401로 응답해 로그인 화면으로 보낸다. */
	@Transactional(readOnly = true)
	public MeResponse getMe(Long userId) {
		return toMe(findMe(userId));
	}

	/** 별명 정하기·바꾸기. 비우면 별명을 지우고 구글 이름으로 돌아간다. */
	@Transactional
	public MeResponse changeNickname(Long userId, String rawNickname) {
		User user = findMe(userId);
		user.changeNickname(Nicknames.normalize(rawNickname));
		log.info("별명 변경: userId={}, 별명 있음={}", userId, user.getNickname() != null);
		return toMe(user);
	}

	private User findMe(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("다시 로그인해 주세요."));
	}

	private MeResponse toMe(User user) {
		Long lastVisitedOrgId = membershipRepository.findFirstByUserIdOrderByLastVisitedAtDesc(user.getId())
			.map(Membership::getOrganizationId)
			.orElse(null);
		return new MeResponse(user.getId(), user.getDisplayName(), user.getNickname(), user.getName(),
				user.getEmail(), user.getProfileImageUrl(), lastVisitedOrgId, user.isAdmin());
	}

	private void requireNotBlocked(String googleSub) {
		blockedAccountRepository.findByGoogleSub(googleSub).ifPresent(block -> {
			throw new BlockedAccountException(block.getId());
		});
	}

	/** 명단에 표시할 이름. 구글 이름이 없으면 이메일 앞부분을 쓴다. */
	private static String displayName(String name, String email) {
		String displayName = StringUtils.hasText(name) ? name.strip() : email.substring(0, email.indexOf('@'));
		return displayName.length() > NAME_MAX_LENGTH ? displayName.substring(0, NAME_MAX_LENGTH) : displayName;
	}
}
