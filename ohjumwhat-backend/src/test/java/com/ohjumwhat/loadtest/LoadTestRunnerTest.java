package com.ohjumwhat.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import tools.jackson.databind.json.JsonMapper;

import com.ohjumwhat.IntegrationTest;

/** 러너의 실행 부분(인자 → 시드/정리 → 파일 → 종료 코드)을 프로필 없이 직접 부른다. JVM 종료는 여기서 하지 않는다. */
class LoadTestRunnerTest extends IntegrationTest {

	@Autowired
	LoadTestSeeder seeder;

	@Autowired
	JsonMapper jsonMapper;

	@TempDir
	Path dir;

	LoadTestRunner runner;

	@BeforeEach
	void setUp() {
		clock.set(2026, 9, 30, 11, 0);
		runner = new LoadTestRunner(seeder, new DefaultApplicationArguments(), jsonMapper, clock);
	}

	@Test
	void 시드하면_JSON과_CSV를_쓰고_0으로_끝난다() throws IOException {
		Path out = dir.resolve("out/seed.json");

		int code = runner.execute(new DefaultApplicationArguments("--ohjumwhat.loadtest.mode=seed",
				"--ohjumwhat.loadtest.orgs=2", "--ohjumwhat.loadtest.members=2", "--ohjumwhat.loadtest.closes-in=PT30M",
				"--ohjumwhat.loadtest.out=" + out));

		assertThat(code).isEqualTo(LoadTestRunner.EXIT_OK);
		SeedResult result = jsonMapper.readValue(Files.readString(out), SeedResult.class);
		assertThat(result.orgs()).hasSize(2);
		assertThat(result.closesAt()).isEqualTo(Instant.now(clock).plusSeconds(30 * 60));
		List<String> csv = Files.readAllLines(dir.resolve("out/seed.csv"));
		assertThat(csv).hasSize(5);
		assertThat(csv.getFirst()).isEqualTo("userId,orgId,pollId,optionIds,sessionCookie");
		SeedResult.Org org = result.orgs().getFirst();
		SeedResult.Member member = org.members().getFirst();
		assertThat(csv.get(1)).isEqualTo(member.userId() + "," + org.orgId() + "," + org.pollId() + ","
				+ org.optionIds().getFirst() + ";" + org.optionIds().get(1) + ";" + org.optionIds().get(2) + ";"
				+ org.optionIds().get(3) + "," + member.sessionCookie());
	}

	@Test
	void 마감_시각을_오늘의_한국_시각으로_읽는다() throws IOException {
		Path out = dir.resolve("seed.json");

		int code = runner.execute(new DefaultApplicationArguments("--ohjumwhat.loadtest.mode=seed",
				"--ohjumwhat.loadtest.orgs=1", "--ohjumwhat.loadtest.members=1", "--ohjumwhat.loadtest.closes-at=12:05",
				"--ohjumwhat.loadtest.out=" + out));

		assertThat(code).isEqualTo(LoadTestRunner.EXIT_OK);
		SeedResult result = jsonMapper.readValue(Files.readString(out), SeedResult.class);
		assertThat(result.closesAt()).isEqualTo(Instant.parse("2026-09-30T03:05:00Z"));
	}

	@Test
	void 인자가_틀리면_2_실행에_실패하면_1이다() {
		assertThat(runner.execute(new DefaultApplicationArguments())).isEqualTo(LoadTestRunner.EXIT_USAGE);
		// 지나간 마감 시각: 시드가 거절한다
		assertThat(runner.execute(new DefaultApplicationArguments("--ohjumwhat.loadtest.mode=seed",
				"--ohjumwhat.loadtest.closes-at=09:00", "--ohjumwhat.loadtest.out=" + dir.resolve("seed.json"))))
			.isEqualTo(LoadTestRunner.EXIT_FAILED);
		assertThat(Files.exists(dir.resolve("seed.json"))).isFalse();
	}

	@Test
	void 두_번_시드하면_실패하고_정리하면_0이다() {
		DefaultApplicationArguments seed = new DefaultApplicationArguments("--ohjumwhat.loadtest.mode=seed",
				"--ohjumwhat.loadtest.orgs=1", "--ohjumwhat.loadtest.members=1",
				"--ohjumwhat.loadtest.out=" + dir.resolve("seed.json"));

		assertThat(runner.execute(seed)).isEqualTo(LoadTestRunner.EXIT_OK);
		assertThat(runner.execute(seed)).isEqualTo(LoadTestRunner.EXIT_FAILED);
		assertThat(runner.execute(new DefaultApplicationArguments("--ohjumwhat.loadtest.mode=clean")))
			.isEqualTo(LoadTestRunner.EXIT_OK);
		assertThat(seeder.exists()).isFalse();
	}

	@Test
	void CSV_경로는_JSON과_같은_이름이다() {
		assertThat(LoadTestRunner.csvPath(Path.of("/out/seed.json"))).isEqualTo(Path.of("/out/seed.csv"));
		assertThat(LoadTestRunner.csvPath(Path.of("seed"))).isEqualTo(Path.of("seed.csv"));
	}
}
