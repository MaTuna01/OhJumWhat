package com.ohjumwhat.place;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * naver.me 단축 링크를 한 번만 요청해 첫 리디렉션의 Location을 읽는다(리디렉션은 따라가지 않는다).
 * 사용자가 붙인 글은 요청에 쓰지 않고, 검증한 코드로 https://naver.me/{코드}를 만들어 요청한다(SSRF 방지).
 */
@Slf4j
@Component
class JdkNaverShortLinks implements NaverShortLinks {

	private static final URI BASE = URI.create("https://naver.me/");

	private static final Pattern CODE = Pattern.compile("[A-Za-z0-9]{4,16}");

	private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

	/** naver.me가 없는 코드에 주는 응답(잘못 붙인 링크) */
	private static final Set<Integer> GONE = Set.of(404, 410);

	private final HttpClient client = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NEVER)
		.connectTimeout(Duration.ofSeconds(2))
		.build();

	@Override
	public Optional<String> location(String code) {
		if (code == null || !CODE.matcher(code).matches()) {
			return Optional.empty();
		}
		HttpRequest request = HttpRequest.newBuilder(BASE.resolve(code))
			.timeout(Duration.ofSeconds(3))
			.header("User-Agent", "Mozilla/5.0 (compatible; ohjumwhat)")
			.GET()
			.build();
		try {
			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			if (GONE.contains(response.statusCode())) {
				log.info("naver.me 없는 코드: status={}", response.statusCode());
				throw new NotFound();
			}
			if (!REDIRECTS.contains(response.statusCode())) {
				log.warn("naver.me 확인 실패: status={}", response.statusCode());
				return Optional.empty();
			}
			return response.headers().firstValue("Location").flatMap(JdkNaverShortLinks::absolute);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
		catch (IOException | IllegalArgumentException e) {
			log.warn("naver.me 확인 실패: {}", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** 상대 주소면 https://naver.me/ 기준으로 푼다. 주소가 아니면 빈 값 */
	static Optional<String> absolute(String location) {
		try {
			return Optional.of(BASE.resolve(location.strip()).toString());
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
