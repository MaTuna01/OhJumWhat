package com.ohjumwhat.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.common.TimeConfig;

/** 막힘 판정 규칙(화면 lib/sanctions.ts restrictionOf와 같다)과 423 문구 */
class SanctionGuardTest {

	/** 2026-10-07(수) 오전 11:00 한국 시간 */
	static final Instant NOW = ZonedDateTime.of(2026, 10, 7, 11, 0, 0, 0, TimeConfig.KST).toInstant();

	@Test
	void 그_기능이나_활동_정지가_든_활성_제재가_있으면_막힌다() {
		List<UserSanction> sanctions = List.of(sanction(List.of(Restriction.CHAT), days(7)));

		assertThat(SanctionGuard.blocking(sanctions, Restriction.CHAT, NOW))
			.contains(new SanctionGuard.Block(NOW.plus(days(7)), false));
		assertThat(SanctionGuard.blocking(sanctions, Restriction.LETTER, NOW)).isEmpty();
		assertThat(SanctionGuard.blocking(sanctions, Restriction.SUSPEND, NOW)).isEmpty();

		List<UserSanction> suspended = List.of(sanction(List.of(Restriction.SUSPEND), null));
		for (Restriction feature : Restriction.values()) {
			assertThat(SanctionGuard.blocking(suspended, feature, NOW)).contains(new SanctionGuard.Block(null, true));
		}
	}

	@Test
	void 끝났거나_해제됐거나_제한이_없는_제재는_막지_않는다() {
		UserSanction expired = sanction(List.of(Restriction.CHAT), days(1));
		UserSanction lifted = sanction(List.of(Restriction.CHAT), null);
		lifted.lift(NOW, 1L);
		UserSanction warning = sanction(List.of(), null);

		Instant end = NOW.plus(days(1));
		assertThat(SanctionGuard.blocking(List.of(expired), Restriction.CHAT, end.minusSeconds(60))).isPresent();
		assertThat(SanctionGuard.blocking(List.of(expired), Restriction.CHAT, end)).isEmpty();
		assertThat(SanctionGuard.blocking(List.of(lifted, warning), Restriction.CHAT, NOW)).isEmpty();
	}

	@Test
	void 겹치면_가장_늦게_끝나는_시각이고_하나라도_무기한이면_무기한이다() {
		UserSanction oneDay = sanction(List.of(Restriction.CHAT), days(1));
		UserSanction week = sanction(List.of(Restriction.CHAT, Restriction.LETTER), days(7));
		UserSanction forever = sanction(List.of(Restriction.CHAT), null);

		assertThat(SanctionGuard.blocking(List.of(oneDay, week), Restriction.CHAT, NOW))
			.contains(new SanctionGuard.Block(NOW.plus(days(7)), false));
		assertThat(SanctionGuard.blocking(List.of(oneDay, forever, week), Restriction.CHAT, NOW))
			.contains(new SanctionGuard.Block(null, false));
		// 무기한 채팅 제한은 쪽지와 상관없다.
		assertThat(SanctionGuard.blocking(List.of(oneDay, forever, week), Restriction.LETTER, NOW))
			.contains(new SanctionGuard.Block(NOW.plus(days(7)), false));
	}

	@Test
	void 활동_정지가_함께_막으면_활동_정지_문구로_그_제재들_중_가장_늦은_시각이다() {
		UserSanction suspend = sanction(List.of(Restriction.SUSPEND), days(1));
		UserSanction chat = sanction(List.of(Restriction.CHAT), days(7));

		Optional<SanctionGuard.Block> chatBlock = SanctionGuard.blocking(List.of(suspend, chat), Restriction.CHAT, NOW);
		assertThat(chatBlock).contains(new SanctionGuard.Block(NOW.plus(days(7)), true));
		assertThat(SanctionGuard.message(Restriction.CHAT, chatBlock.orElseThrow()))
			.isEqualTo("활동이 정지된 상태예요(10월 14일 오전 11:00까지). 투표 참여만 할 수 있어요.");

		Optional<SanctionGuard.Block> letterBlock = SanctionGuard.blocking(List.of(suspend, chat), Restriction.LETTER,
				NOW);
		assertThat(letterBlock).contains(new SanctionGuard.Block(NOW.plus(days(1)), true));
	}

	@Test
	void 문구는_한국_시간_오전_오후로_적고_무기한이면_해제될_때까지다() {
		Instant evening = ZonedDateTime.of(2026, 10, 14, 18, 0, 0, 0, TimeConfig.KST).toInstant();
		Instant midnight = ZonedDateTime.of(2026, 1, 2, 0, 5, 0, 0, TimeConfig.KST).toInstant();

		assertThat(SanctionGuard.until(evening)).isEqualTo("10월 14일 오후 6:00까지");
		assertThat(SanctionGuard.until(midnight)).isEqualTo("1월 2일 오전 12:05까지");
		assertThat(SanctionGuard.until(null)).isEqualTo("해제될 때까지");

		SanctionGuard.Block block = new SanctionGuard.Block(evening, false);
		assertThat(SanctionGuard.message(Restriction.POLL, block))
			.isEqualTo("관리자가 메뉴 올리기·댓글·투표 관리를 제한했어요(10월 14일 오후 6:00까지). 투표 참여는 할 수 있어요.");
		assertThat(SanctionGuard.message(Restriction.CHAT, block)).isEqualTo("관리자가 채팅을 제한했어요(10월 14일 오후 6:00까지).");
		assertThat(SanctionGuard.message(Restriction.LETTER, block))
			.isEqualTo("관리자가 쪽지 보내기를 제한했어요(10월 14일 오후 6:00까지).");
		assertThat(SanctionGuard.message(Restriction.GUESTBOOK, block))
			.isEqualTo("관리자가 방명록 쓰기를 제한했어요(10월 14일 오후 6:00까지).");
		assertThat(SanctionGuard.message(Restriction.PROFILE, new SanctionGuard.Block(null, false)))
			.isEqualTo("관리자가 프로필 수정을 제한했어요(해제될 때까지).");
		assertThat(SanctionGuard.message(Restriction.SUSPEND, new SanctionGuard.Block(null, true)))
			.isEqualTo("활동이 정지된 상태예요(해제될 때까지). 투표 참여만 할 수 있어요.");
	}

	@Test
	void 상태는_제한_초기화_해제_끝나는_시각으로_정한다() {
		UserSanction active = sanction(List.of(Restriction.CHAT), days(1));
		UserSanction lifted = sanction(List.of(Restriction.CHAT), days(1));
		lifted.lift(NOW, 1L);
		UserSanction warning = sanction(List.of(), null);
		UserSanction resetOnly = new UserSanction(1L, List.of(), EnumSet.of(ProfileReset.PHOTO), SanctionReason.PROFILE,
				null, NOW, null, 1L);

		assertThat(active.status(NOW)).isEqualTo(SanctionStatus.ACTIVE);
		assertThat(active.status(NOW.plus(days(1)))).isEqualTo(SanctionStatus.EXPIRED);
		assertThat(lifted.status(NOW)).isEqualTo(SanctionStatus.LIFTED);
		assertThat(lifted.status(NOW.plus(days(2)))).isEqualTo(SanctionStatus.LIFTED);
		assertThat(warning.status(NOW)).isEqualTo(SanctionStatus.WARNING);
		assertThat(resetOnly.status(NOW)).isEqualTo(SanctionStatus.RESET_ONLY);
	}

	private static UserSanction sanction(List<Restriction> restrictions, Duration duration) {
		return new UserSanction(1L, restrictions, List.of(), SanctionReason.ABUSE, null, NOW,
				duration == null ? null : NOW.plus(duration), 1L);
	}

	private static Duration days(int days) {
		return Duration.ofDays(days);
	}
}
