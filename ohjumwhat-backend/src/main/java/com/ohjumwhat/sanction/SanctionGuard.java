package com.ohjumwhat.sanction;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ohjumwhat.common.ApiException;
import com.ohjumwhat.common.TimeConfig;

/**
 * 기능이 제재로 막혔는지 판정한다. 규칙은 화면의 lib/sanctions.ts restrictionOf와 같아서 함께 고친다.
 * <ul>
 * <li>활성 제재 = 제한이 있고, 해제되지 않았고, 끝나는 시각 전(UserSanction.status가 ACTIVE)</li>
 * <li>기능 X가 막힘 = 활성 제재 중 X 또는 SUSPEND가 든 것이 하나라도 있음</li>
 * <li>끝나는 시각 = 그 제재들 중 가장 늦은 것, 하나라도 해제할 때까지(null)면 null</li>
 * <li>suspended = 그 제재들 중 SUSPEND가 있음(문구를 활동 정지로 바꾼다)</li>
 * </ul>
 */
@Component
public class SanctionGuard {

	/** 끝나는 시각 문구: 「10월 14일 오후 6:00」(한국 시간) */
	private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
		.withZone(TimeConfig.KST);

	private final UserSanctionRepository sanctionRepository;

	private final Clock clock;

	SanctionGuard(UserSanctionRepository sanctionRepository, Clock clock) {
		this.sanctionRepository = sanctionRepository;
		this.clock = clock;
	}

	/** 지금 걸려 있는 제재(오래된 순). GET /api/me의 sanctions */
	public List<ActiveSanction> activeSanctions(Long userId) {
		return sanctionRepository.findActive(userId, Instant.now(clock)).stream().map(ActiveSanction::of).toList();
	}

	/** 그 기능이 막혔으면 423과 문구(SanctionInterceptor가 @Restricted 핸들러 앞에서 부른다). 활성 제재는 한 쿼리로 읽는다. */
	public void check(Long userId, Restriction feature) {
		Instant now = Instant.now(clock);
		blocking(sanctionRepository.findActive(userId, now), feature, now).ifPresent(block -> {
			throw ApiException.locked(message(feature, block));
		});
	}

	/** 그 기능을 막는 활성 제재들의 끝나는 시각(null = 해제할 때까지)과 활동 정지 여부. 막히지 않았으면 비어 있다. */
	static Optional<Block> blocking(List<UserSanction> sanctions, Restriction feature, Instant now) {
		List<UserSanction> matching = sanctions.stream()
			.filter(s -> s.status(now) == SanctionStatus.ACTIVE)
			.filter(s -> s.getRestrictions().contains(feature) || s.getRestrictions().contains(Restriction.SUSPEND))
			.toList();
		if (matching.isEmpty()) {
			return Optional.empty();
		}
		boolean suspended = matching.stream().anyMatch(s -> s.getRestrictions().contains(Restriction.SUSPEND));
		boolean forever = matching.stream().anyMatch(s -> s.getEndsAt() == null);
		Instant endsAt = forever ? null
				: matching.stream().map(UserSanction::getEndsAt).max(Instant::compareTo).orElseThrow();
		return Optional.of(new Block(endsAt, suspended));
	}

	/** 423 문구. 활동 정지 때문이면 활동 정지 문구, 아니면 그 기능의 문구 */
	static String message(Restriction feature, Block block) {
		return (block.suspended() ? Restriction.SUSPEND : feature).message(until(block.endsAt()));
	}

	/** 「10월 14일 오후 6:00까지」, 끝이 없으면 「해제될 때까지」 */
	static String until(Instant endsAt) {
		return endsAt == null ? "해제될 때까지" : UNTIL.format(endsAt) + "까지";
	}

	/**
	 * @param endsAt 끝나는 시각(null = 해제할 때까지)
	 * @param suspended 활동 정지가 들어 있다
	 */
	record Block(Instant endsAt, boolean suspended) {
	}
}
