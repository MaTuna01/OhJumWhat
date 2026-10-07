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
import com.ohjumwhat.sanction.SanctionGuard;

@Slf4j
@Service
public class UserService {

	private static final int NAME_MAX_LENGTH = 100;

	private final UserRepository userRepository;

	private final MembershipRepository membershipRepository;

	private final BlockedAccountRepository blockedAccountRepository;

	private final AdminProperties adminProperties;

	private final ProfilePhotoStorage photoStorage;

	private final SanctionGuard sanctionGuard;

	private final Clock clock;

	public UserService(UserRepository userRepository, MembershipRepository membershipRepository,
			BlockedAccountRepository blockedAccountRepository, AdminProperties adminProperties,
			ProfilePhotoStorage photoStorage, SanctionGuard sanctionGuard, Clock clock) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.blockedAccountRepository = blockedAccountRepository;
		this.adminProperties = adminProperties;
		this.photoStorage = photoStorage;
		this.sanctionGuard = sanctionGuard;
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

	/**
	 * 별명 정하기·바꾸기. 비우면 별명을 지우고 구글 이름으로 돌아간다. 로그인과 겹쳐도 되쓰이지 않게
	 * 엔티티가 아니라 update 쿼리로 저장한다.
	 */
	@Transactional
	public MeResponse changeNickname(Long userId, String rawNickname) {
		if (!saveNickname(userId, rawNickname)) {
			throw ApiException.unauthorized("다시 로그인해 주세요.");
		}
		User user = findMe(userId);
		log.info("별명 변경: userId={}, 별명 있음={}", userId, user.getNickname() != null);
		return toMe(user);
	}

	/**
	 * 한줄 소개와 좋아하는 음식을 함께 바꾼다(통째로 바꾸기, 비우면 지운다). 로그인과 겹쳐도 되쓰이지 않게
	 * 엔티티가 아니라 update 쿼리로 저장한다.
	 */
	@Transactional
	public MeResponse changeIntro(Long userId, String rawBio, List<String> rawFoodTags) {
		if (!saveIntro(userId, rawBio, rawFoodTags)) {
			throw ApiException.unauthorized("다시 로그인해 주세요.");
		}
		User user = findMe(userId);
		log.info("프로필 소개 변경: userId={}, 소개 있음={}, 음식 {}개", userId, user.getBio() != null,
				user.getFoodTags().size());
		return toMe(user);
	}

	/**
	 * 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급)을 통째로 바꾼다. 다섯 항목 모두 필수라 본인은 지울 수 없다(관리자의
	 * 「상세 프로필 지우기」만 UserRepository.clearDetails로 한꺼번에 비운다).
	 * 화면의 입력 순서대로 확인해 처음 걸린 항목의 문구로 답한다. 소개처럼 update 쿼리로 저장한다.
	 */
	@Transactional
	public MeResponse changeDetails(Long userId, String rawMbti, String rawPersonalColor, List<String> rawHobbies,
			Integer rawAge, String rawJobTitle) {
		if (!saveDetails(userId, rawMbti, rawPersonalColor, rawHobbies, rawAge, rawJobTitle)) {
			throw ApiException.unauthorized("다시 로그인해 주세요.");
		}
		User user = findMe(userId);
		log.info("상세 프로필 변경: userId={}, 취미 {}개", userId,
				user.getDetails() == null ? 0 : user.getDetails().hobbies().size());
		return toMe(user);
	}

	/*
	 * 아래 세 메서드는 마이페이지(위)와 관리자 콘솔(AdminService)이 같은 규칙·같은 방법으로 저장하도록 함께 쓴다.
	 * 회원을 찾지 못했을 때의 응답(401·404)과 로그는 부르는 쪽이 정한다.
	 */

	/**
	 * 별명을 정리해(Nicknames 규칙, 비우면 지워 구글 이름으로 돌아간다) update 쿼리로 저장한다. 회원이 없으면 false.
	 * 영속성 컨텍스트를 비우므로 저장한 뒤에는 회원을 다시 읽는다.
	 */
	@Transactional
	public boolean saveNickname(Long userId, String rawNickname) {
		return userRepository.updateNickname(userId, Nicknames.normalize(rawNickname)) > 0;
	}

	/**
	 * 한줄 소개와 좋아하는 음식을 정리해(ProfileIntro 규칙, 비우면 지운다) update 쿼리로 저장한다. 회원이 없으면 false.
	 * 영속성 컨텍스트를 비우므로 저장한 뒤에는 회원을 다시 읽는다.
	 */
	@Transactional
	public boolean saveIntro(Long userId, String rawBio, List<String> rawFoodTags) {
		String bio = ProfileIntro.bio(rawBio);
		List<String> foodTags = ProfileIntro.foodTags(rawFoodTags);
		return userRepository.updateIntro(userId, bio, foodTags.toArray(String[]::new)) > 0;
	}

	/**
	 * 상세 프로필을 정리해(ProfileDetails 규칙, 화면의 입력 순서대로 확인해 처음 걸린 항목의 문구로 400) update 쿼리로 저장한다.
	 * 회원이 없으면 false. 영속성 컨텍스트를 비우므로 저장한 뒤에는 회원을 다시 읽는다.
	 */
	@Transactional
	public boolean saveDetails(Long userId, String rawMbti, String rawPersonalColor, List<String> rawHobbies,
			Integer rawAge, String rawJobTitle) {
		String mbti = ProfileDetails.mbti(rawMbti);
		PersonalColor personalColor = ProfileDetails.personalColor(rawPersonalColor);
		List<String> hobbies = ProfileDetails.hobbies(rawHobbies);
		short age = ProfileDetails.age(rawAge);
		String jobTitle = ProfileDetails.jobTitle(rawJobTitle);
		return userRepository.updateDetails(userId, mbti, personalColor, hobbies.toArray(String[]::new), age,
				jobTitle) > 0;
	}

	/**
	 * 올린 프로필 사진의 키를 바꾼다(null이면 구글 사진으로 돌아간다). 새 파일은 ProfilePhotoService가 미리 써 두고,
	 * 옛 파일은 커밋한 뒤에 지운다. 같은 회원의 강제 탈퇴·관리자 사진 지우기와 겹치지 않게 행을 잠근다.
	 */
	@Transactional
	public MeResponse changePhoto(Long userId, String photoKey) {
		User user = userRepository.findByIdForUpdate(userId)
			.orElseThrow(() -> ApiException.unauthorized("다시 로그인해 주세요."));
		String oldKey = user.getPhotoKey();
		if (photoKey != null || oldKey != null) {
			userRepository.updatePhotoKey(userId, photoKey);
			photoStorage.deleteAfterCommit(oldKey);
			log.info("프로필 사진 변경: userId={}, 올린 사진 있음={}", userId, photoKey != null);
		}
		return toMe(findMe(userId));
	}

	private User findMe(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("다시 로그인해 주세요."));
	}

	/** 내 정보 응답. 지금 걸려 있는 제재(sanctions)도 여기서만 넣는다(프로필을 바꾼 응답에도 함께 간다). */
	private MeResponse toMe(User user) {
		Long lastVisitedOrgId = membershipRepository.findFirstByUserIdOrderByLastVisitedAtDesc(user.getId())
			.map(Membership::getOrganizationId)
			.orElse(null);
		return new MeResponse(user.getId(), user.getDisplayName(), user.getNickname(), user.getName(),
				user.getEmail(), user.getPhotoUrl(), user.getProfileImageUrl(), user.getPhotoKey() != null,
				user.getBio(), user.getFoodTags(), user.getDetails(), lastVisitedOrgId, user.isAdmin(),
				sanctionGuard.activeSanctions(user.getId()));
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
