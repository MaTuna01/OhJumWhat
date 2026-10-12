package com.ohjumwhat.loadtest;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.boot.ApplicationArguments;

/**
 * 시드/정리 명령의 인자. 모두 {@code --ohjumwhat.loadtest.<이름>=값} 형식이다.
 *
 * @param mode seed 또는 clean(필수)
 * @param orgs 조직 수(seed)
 * @param members 조직당 멤버 수(seed)
 * @param closesAt 투표 마감 시각(한국 시간 HH:mm, 오늘). closesIn과 함께 쓸 수 없다
 * @param closesIn 지금부터 마감까지(ISO-8601, 예: PT30M). 둘 다 없으면 3시간
 * @param prevote 미리 참여시킬 멤버 비율(0~1)
 * @param history 조직마다 만들 지난 투표 수(하루에 하나, 어제부터 거슬러). 메뉴 통계·자동완성·랭킹에 기록이 있는 상태를 만든다
 * @param out 결과 파일(JSON). 같은 이름의 .csv도 함께 쓴다
 */
record LoadTestArgs(Mode mode, int orgs, int members, LocalTime closesAt, Duration closesIn, double prevote,
		int history, Path out) {

	enum Mode {
		SEED, CLEAN
	}

	static final String PREFIX = "ohjumwhat.loadtest.";

	static final Duration DEFAULT_CLOSES_IN = Duration.ofHours(3);

	static final int MAX_ORGS = 1000;

	static final int MAX_MEMBERS = 500;

	static final int MAX_HISTORY = 3650;

	static LoadTestArgs parse(ApplicationArguments args) {
		Mode mode = switch (value(args, "mode", "")) {
			case "seed" -> Mode.SEED;
			case "clean" -> Mode.CLEAN;
			case "" -> throw new IllegalArgumentException("--" + PREFIX + "mode=seed|clean 을 지정해 주세요.");
			default -> throw new IllegalArgumentException("mode는 seed 또는 clean입니다.");
		};
		int orgs = intValue(args, "orgs", 15, 1, MAX_ORGS);
		int members = intValue(args, "members", 20, 1, MAX_MEMBERS);
		String closesAtText = value(args, "closes-at", "");
		String closesInText = value(args, "closes-in", "");
		if (!closesAtText.isEmpty() && !closesInText.isEmpty()) {
			throw new IllegalArgumentException("closes-at과 closes-in은 함께 쓸 수 없습니다.");
		}
		LocalTime closesAt = null;
		Duration closesIn = null;
		try {
			if (!closesAtText.isEmpty()) {
				closesAt = LocalTime.parse(closesAtText);
			}
			else if (!closesInText.isEmpty()) {
				closesIn = Duration.parse(closesInText);
				if (closesIn.isNegative() || closesIn.isZero()) {
					throw new IllegalArgumentException("closes-in은 0보다 커야 합니다.");
				}
			}
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("closes-at은 HH:mm, closes-in은 PT30M 형식입니다.", e);
		}
		double prevote;
		try {
			prevote = Double.parseDouble(value(args, "prevote", "0.5"));
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("prevote는 0~1 사이의 수입니다.", e);
		}
		if (prevote < 0 || prevote > 1) {
			throw new IllegalArgumentException("prevote는 0~1 사이의 수입니다.");
		}
		int history = intValue(args, "history", 0, 0, MAX_HISTORY);
		Path out = Path.of(value(args, "out", "seed.json"));
		return new LoadTestArgs(mode, orgs, members, closesAt, closesIn, prevote, history, out);
	}

	private static String value(ApplicationArguments args, String name, String defaultValue) {
		List<String> values = args.getOptionValues(PREFIX + name);
		if (values == null || values.isEmpty()) {
			return defaultValue;
		}
		if (values.size() > 1) {
			throw new IllegalArgumentException(name + "은(는) 한 번만 지정합니다.");
		}
		return values.getFirst().strip();
	}

	private static int intValue(ApplicationArguments args, String name, int defaultValue, int min, int max) {
		String text = value(args, name, String.valueOf(defaultValue));
		int number;
		try {
			number = Integer.parseInt(text);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException(name + "은(는) 정수입니다.", e);
		}
		if (number < min || number > max) {
			throw new IllegalArgumentException(name + "은(는) " + min + "~" + max + " 사이입니다.");
		}
		return number;
	}
}
