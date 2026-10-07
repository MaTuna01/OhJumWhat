package com.ohjumwhat.sanction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.UserText;
import com.ohjumwhat.user.ProfilePhotoStorage;
import com.ohjumwhat.user.User;
import com.ohjumwhat.user.UserRepository;

/**
 * 이용 제한(제재): 관리자가 거는 제한·프로필 초기화·경고와, 본인에게 보여줄 안내. 막힌 기능의 판정은 SanctionGuard가 한다.
 * 로그에는 ID와 종류만 남긴다(사유 설명은 남기지 않는다).
 */
@Slf4j
@Service
public class SanctionService {

	/** 고를 수 있는 기간(일). 고르지 않으면(null) 해제할 때까지 */
	static final Set<Integer> DAYS = Set.of(1, 3, 7, 30);

	static final int NOTE_MAX_LENGTH = 200;

	static final int ALERT_LIMIT = 20;

	static final int SEEN_LIMIT = 50;

	static final int LIST_LIMIT = 100;

	private static final String USER_NOT_FOUND = "회원을 찾을 수 없어요.";

	private final UserSanctionRepository sanctionRepository;

	private final UserRepository userRepository;

	private final ProfilePhotoStorage photoStorage;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	SanctionService(UserSanctionRepository sanctionRepository, UserRepository userRepository,
			ProfilePhotoStorage photoStorage, ApplicationEventPublisher events, Clock clock) {
		this.sanctionRepository = sanctionRepository;
		this.userRepository = userRepository;
		this.photoStorage = photoStorage;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 제재 걸기. 회원 행을 잠가(강제 탈퇴·다른 관리자의 처리와 겹치지 않게) 확인한 뒤 제재를 저장하고, 고른 프로필 항목을 비운다.
	 * 제한·초기화가 모두 없으면 경고다. 프로필 초기화는 마이페이지·관리자 프로필 수정과 같은 update 쿼리로 하고(로그인이 되쓰지
	 * 않게), 올린 사진 파일은 커밋한 뒤에 지운다. 본인에게는 커밋 뒤 푸시(SanctionAppliedEvent)와 다음 화면의 안내 창으로 알린다.
	 *
	 * @param days 기간(1·3·7·30일). null이면 해제할 때까지. 제한이 없으면 null이어야 한다
	 */
	@Transactional
	public SanctionRow apply(Long adminId, Long userId, List<String> rawRestrictions, List<String> rawResets,
			Integer days, String rawReason, String rawNote) {
		User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
		if (user.getId().equals(adminId)) {
			throw ApiException.conflict("자기 자신은 제재할 수 없어요.");
		}
		if (user.isAdmin()) {
			throw ApiException.conflict("관리자는 제재할 수 없어요.");
		}
		// 초기화로 지울 사진 파일. update 쿼리가 영속성 컨텍스트를 비우기 전에 읽어 둔다.
		String oldPhotoKey = user.getPhotoKey();

		EnumSet<Restriction> restrictions = restrictions(rawRestrictions);
		EnumSet<ProfileReset> resets = resets(rawResets);
		// 지금 비어 있는 항목은 초기화할 것이 없어 기록에서도 뺀다(본인 안내에 하지 않은 초기화가 보이지 않게).
		// 화면은 빈 항목을 고르지 못하게 하므로, 그사이 다른 관리자가 먼저 비운 경우다.
		resets.removeIf(reset -> nothingToReset(user, reset));
		SanctionReason reason = reason(rawReason);
		if (days != null && !DAYS.contains(days)) {
			throw ApiException.badRequest("기간은 1일·3일·7일·30일 중에서 골라 주세요.");
		}
		if (days != null && restrictions.isEmpty()) {
			throw ApiException.badRequest("제한을 고르지 않으면 기간을 정할 수 없어요.");
		}
		String note = note(rawNote);

		Instant now = Instant.now(clock);
		Instant endsAt = days == null ? null : now.plus(Duration.ofDays(days));
		UserSanction sanction = sanctionRepository
			.save(new UserSanction(userId, restrictions, resets, reason, note, now, endsAt, adminId));
		Long sanctionId = sanction.getId();
		// 아래 update 쿼리는 영속성 컨텍스트를 비운다. 그 뒤로는 엔티티를 건드리지 않고 ID로 다시 읽는다.
		if (resets.contains(ProfileReset.NICKNAME)) {
			userRepository.updateNickname(userId, null);
		}
		if (resets.contains(ProfileReset.PHOTO) && oldPhotoKey != null) {
			userRepository.updatePhotoKey(userId, null);
			photoStorage.deleteAfterCommit(oldPhotoKey);
		}
		if (resets.contains(ProfileReset.INTRO)) {
			userRepository.updateIntro(userId, null, new String[0]);
		}
		if (resets.contains(ProfileReset.DETAILS)) {
			userRepository.clearDetails(userId);
		}
		events.publishEvent(new SanctionAppliedEvent(sanctionId, userId));
		log.info("관리자 제재: adminId={}, userId={}, sanctionId={}, restrictions={}, resets={}, days={}", adminId, userId,
				sanctionId, restrictions, resets, days);
		return row(sanctionId, now);
	}

	/** 이 항목이 지금 비어 있어 초기화할 것이 없다 */
	private static boolean nothingToReset(User user, ProfileReset reset) {
		return switch (reset) {
			case NICKNAME -> user.getNickname() == null;
			case PHOTO -> user.getPhotoKey() == null;
			case INTRO -> user.getBio() == null && user.getFoodTags().isEmpty();
			case DETAILS -> user.getDetails() == null;
		};
	}

	/** 회원 상세의 제재 기록(최신순 100건). 없는 회원은 404 */
	@Transactional(readOnly = true)
	public List<SanctionRow> ofUser(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw ApiException.notFound(USER_NOT_FOUND);
		}
		Instant now = Instant.now(clock);
		return sanctionRepository.findRowsOfUser(userId, PageRequest.of(0, LIST_LIMIT)).stream().map(source -> source.toRow(now)).toList();
	}

	/** 관리자 「제재」 탭(최신순 100건). activeOnly면 진행 중인 제재만 */
	@Transactional(readOnly = true)
	public List<SanctionRow> list(boolean activeOnly) {
		Instant now = Instant.now(clock);
		return sanctionRepository.findRows(activeOnly, now, PageRequest.of(0, LIST_LIMIT))
			.stream()
			.map(source -> source.toRow(now))
			.toList();
	}

	/**
	 * 해제: 진행 중인 제재만 지금 풀린다. 함께 한 프로필 초기화는 되돌리지 않고, 본인에게 따로 알리지 않는다
	 * (이미 본 안내는 그대로, 아직 보지 않았으면 안내 창에 「해제됨」으로 보인다).
	 */
	@Transactional
	public SanctionRow lift(Long adminId, Long sanctionId) {
		UserSanction sanction = sanctionRepository.findByIdForUpdate(sanctionId)
			.orElseThrow(() -> ApiException.notFound("제재를 찾을 수 없어요."));
		Instant now = Instant.now(clock);
		if (sanction.status(now) != SanctionStatus.ACTIVE) {
			throw ApiException.conflict("이미 끝났거나 해제된 제재예요.");
		}
		sanction.lift(now, adminId);
		sanctionRepository.flush();
		log.info("관리자 제재 해제: adminId={}, userId={}, sanctionId={}", adminId, sanction.getUserId(), sanctionId);
		return row(sanctionId, now);
	}

	/** 아직 안내 창에서 보지 않은 내 제재(오래된 순 20건). 그사이 끝났거나 해제된 것도 상태와 함께 준다. */
	@Transactional(readOnly = true)
	public SanctionAlertsResponse alerts(Long userId) {
		Instant now = Instant.now(clock);
		return new SanctionAlertsResponse(sanctionRepository.findUnseen(userId, PageRequest.of(0, ALERT_LIMIT))
			.stream()
			.map(sanction -> SanctionNotice.of(sanction, now))
			.toList());
	}

	/** 안내 창을 봤다. 내 것이고 아직 보지 않은 것만 바꾸고, 남의 ID나 이미 본 ID는 그대로 둔다. */
	@Transactional
	public void markSeen(Long userId, List<Long> ids) {
		if (ids == null || ids.isEmpty() || ids.size() > SEEN_LIMIT || ids.stream().anyMatch(Objects::isNull)) {
			throw ApiException.badRequest("확인한 안내가 올바르지 않아요.");
		}
		int seen = sanctionRepository.markSeen(userId, Set.copyOf(ids), Instant.now(clock));
		log.debug("제재 안내 확인: userId={}, 확인한 수={}", userId, seen);
	}

	private SanctionRow row(Long sanctionId, Instant now) {
		return sanctionRepository.findRow(sanctionId).orElseThrow().toRow(now);
	}

	/** 중복을 없애고 정의 순서로. 활동 정지가 있으면 활동 정지만 남긴다(다른 제한을 모두 포함한다). */
	private static EnumSet<Restriction> restrictions(List<String> raw) {
		EnumSet<Restriction> restrictions = EnumSet.noneOf(Restriction.class);
		for (String name : raw == null ? List.<String>of() : raw) {
			restrictions.add(parse(Restriction.class, name, "제한할 기능을 다시 골라 주세요."));
		}
		return restrictions.contains(Restriction.SUSPEND) ? EnumSet.of(Restriction.SUSPEND) : restrictions;
	}

	private static EnumSet<ProfileReset> resets(List<String> raw) {
		EnumSet<ProfileReset> resets = EnumSet.noneOf(ProfileReset.class);
		for (String name : raw == null ? List.<String>of() : raw) {
			resets.add(parse(ProfileReset.class, name, "초기화할 항목을 다시 골라 주세요."));
		}
		return resets;
	}

	private static SanctionReason reason(String raw) {
		return parse(SanctionReason.class, raw, "사유를 골라 주세요.");
	}

	/** 관리자 설명: 비면 null, 있으면 UserText 규칙(앞뒤 공백 제거, 줄바꿈 허용, 제어 문자 거절, 200자) */
	private static String note(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return UserText.normalize(raw, NOTE_MAX_LENGTH, true);
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String name, String message) {
		if (name == null) {
			throw ApiException.badRequest(message);
		}
		try {
			return Enum.valueOf(type, name);
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest(message);
		}
	}
}
