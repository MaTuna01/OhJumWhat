package com.ohjumwhat.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class LoadTestArgsTest {

	@Test
	void 기본값은_조직_15개_멤버_20명_절반_참여_seed_json이다() {
		LoadTestArgs args = parse("--ohjumwhat.loadtest.mode=seed");

		assertThat(args).isEqualTo(new LoadTestArgs(LoadTestArgs.Mode.SEED, 15, 20, null, null, 0.5, 0,
				Path.of("seed.json")));
	}

	@Test
	void 모든_인자를_읽는다() {
		LoadTestArgs args = parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.orgs=50",
				"--ohjumwhat.loadtest.members=20", "--ohjumwhat.loadtest.closes-in=PT30M",
				"--ohjumwhat.loadtest.prevote=0", "--ohjumwhat.loadtest.history=30",
				"--ohjumwhat.loadtest.out=/out/seed.json");

		assertThat(args.orgs()).isEqualTo(50);
		assertThat(args.history()).isEqualTo(30);
		assertThat(args.members()).isEqualTo(20);
		assertThat(args.closesIn()).isEqualTo(Duration.ofMinutes(30));
		assertThat(args.closesAt()).isNull();
		assertThat(args.prevote()).isZero();
		assertThat(args.out()).isEqualTo(Path.of("/out/seed.json"));
	}

	@Test
	void 마감_시각은_HH_mm으로_읽는다() {
		LoadTestArgs args = parse("--ohjumwhat.loadtest.mode=clean", "--ohjumwhat.loadtest.closes-at=12:05");

		assertThat(args.mode()).isEqualTo(LoadTestArgs.Mode.CLEAN);
		assertThat(args.closesAt()).isEqualTo(LocalTime.of(12, 5));
	}

	@Test
	void 모드가_없거나_틀리면_거절한다() {
		assertThatThrownBy(() -> parse()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("mode=seed|clean");
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=reset"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 잘못된_값은_거절한다() {
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.closes-at=12:05",
				"--ohjumwhat.loadtest.closes-in=PT1H")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("함께");
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.closes-in=PT0S"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.closes-at=noon"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.orgs=0"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.members=abc"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.prevote=1.5"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.history=-1"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parse("--ohjumwhat.loadtest.mode=seed", "--ohjumwhat.loadtest.orgs=1",
				"--ohjumwhat.loadtest.orgs=2")).isInstanceOf(IllegalArgumentException.class);
	}

	private static LoadTestArgs parse(String... args) {
		return LoadTestArgs.parse(new DefaultApplicationArguments(args));
	}
}
