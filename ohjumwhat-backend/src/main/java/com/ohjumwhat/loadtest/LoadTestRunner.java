package com.ohjumwhat.loadtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.ohjumwhat.common.TimeConfig;

/**
 * 부하 테스트 시드/정리 명령. {@code --spring.profiles.active=loadtest}로만 뜨고(이 클래스만 프로필에 묶인다),
 * 그 프로필은 서버 포트를 열지 않는다(application-loadtest.yml). 앱이 준비되면 한 번 실행하고 종료 코드와 함께 JVM을 끝낸다.
 *
 * <p>ApplicationRunner가 아니라 ApplicationReadyEvent를 쓰고 종료를 별도 스레드에서 하는 이유: SpringApplication.run()이
 * 끝나기 전에 컨텍스트를 닫으면 뒤따르는 ready 이벤트 발행이 닫힌 컨텍스트를 건드린다. ready는 run()의 마지막 단계다.
 * 포트를 열지 않아도 Tomcat의 대기 스레드가 JVM을 붙들고 있어 명시적으로 끝내야 한다.
 *
 * <pre>
 * java -jar app.jar --spring.profiles.active=loadtest --ohjumwhat.loadtest.mode=seed \
 *   --ohjumwhat.loadtest.orgs=15 --ohjumwhat.loadtest.members=20 --ohjumwhat.loadtest.out=seed.json
 * java -jar app.jar --spring.profiles.active=loadtest --ohjumwhat.loadtest.mode=clean
 * </pre>
 *
 * 종료 코드: 0 성공, 1 실행 실패(DB 오류·정리 뒤에도 데이터가 남음), 2 인자 오류.
 */
@Slf4j
@Component
@Profile("loadtest")
class LoadTestRunner implements ApplicationListener<ApplicationReadyEvent> {

	static final int EXIT_OK = 0;

	static final int EXIT_FAILED = 1;

	static final int EXIT_USAGE = 2;

	private final LoadTestSeeder seeder;

	private final ApplicationArguments arguments;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	LoadTestRunner(LoadTestSeeder seeder, ApplicationArguments arguments, JsonMapper jsonMapper, Clock clock) {
		this.seeder = seeder;
		this.arguments = arguments;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		int exitCode = execute(arguments);
		ConfigurableApplicationContext context = event.getApplicationContext();
		Thread exit = new Thread(() -> System.exit(SpringApplication.exit(context, () -> exitCode)), "loadtest-exit");
		exit.setDaemon(false);
		exit.start();
	}

	/** 인자대로 실행하고 종료 코드를 돌려준다(테스트가 직접 부른다). */
	int execute(ApplicationArguments args) {
		LoadTestArgs parsed;
		try {
			parsed = LoadTestArgs.parse(args);
		}
		catch (IllegalArgumentException e) {
			log.error("인자 오류: {}", e.getMessage());
			return EXIT_USAGE;
		}
		try {
			return switch (parsed.mode()) {
				case SEED -> seed(parsed);
				case CLEAN -> clean();
			};
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			log.error("실행 실패: {}", e.getMessage());
			return EXIT_FAILED;
		}
		catch (RuntimeException | IOException e) {
			log.error("실행 실패", e);
			return EXIT_FAILED;
		}
	}

	private int seed(LoadTestArgs args) throws IOException {
		Instant now = Instant.now(clock);
		Instant closesAt;
		if (args.closesAt() != null) {
			closesAt = ZonedDateTime.of(LocalDate.ofInstant(now, TimeConfig.KST), args.closesAt(), TimeConfig.KST)
				.toInstant();
		}
		else {
			closesAt = now.plus(args.closesIn() != null ? args.closesIn() : LoadTestArgs.DEFAULT_CLOSES_IN);
		}
		SeedResult result = seeder.seed(new LoadTestSeeder.SeedSpec(args.orgs(), args.members(), closesAt,
				args.prevote()));
		write(args.out(), result);
		return EXIT_OK;
	}

	private int clean() {
		LoadTestSeeder.CleanResult result = seeder.clean();
		if (seeder.exists()) {
			log.error("정리 뒤에도 부하 테스트 데이터가 남아 있습니다(건너뛴 조직: {}). 직접 확인해 주세요.", result.skippedOrgIds());
			return EXIT_FAILED;
		}
		return EXIT_OK;
	}

	/** JSON과 같은 이름의 CSV(userId,orgId,pollId,optionIds,sessionCookie)를 쓴다. 로그인 쿠키가 들어 있으니 다루기 조심한다. */
	private void write(Path out, SeedResult result) throws IOException {
		Path dir = out.toAbsolutePath().getParent();
		if (dir != null) {
			Files.createDirectories(dir);
		}
		Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
		List<String> lines = new ArrayList<>();
		lines.add("userId,orgId,pollId,optionIds,sessionCookie");
		for (SeedResult.Org org : result.orgs()) {
			String optionIds = org.optionIds().stream().map(String::valueOf).collect(Collectors.joining(";"));
			for (SeedResult.Member member : org.members()) {
				lines.add(member.userId() + "," + org.orgId() + "," + org.pollId() + "," + optionIds + ","
						+ member.sessionCookie());
			}
		}
		Files.write(csvPath(out), lines);
		log.info("시드 결과: {} (CSV {})", out.toAbsolutePath(), csvPath(out).toAbsolutePath());
	}

	static Path csvPath(Path out) {
		String name = out.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		return out.resolveSibling(base + ".csv");
	}
}
