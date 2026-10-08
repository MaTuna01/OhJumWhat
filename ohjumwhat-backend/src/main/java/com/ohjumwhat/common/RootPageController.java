package com.ohjumwhat.common;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.ohjumwhat.auth.LoginUser;

/**
 * 첫 화면 "/". 로그인했으면 앱(index.html, 프론트 RootRedirect가 갈 곳을 정한다),
 * 로그인하지 않았으면 소개 페이지(landing.html)를 준다. 검색 로봇은 로그인하지 않으므로 소개 페이지만 색인한다
 * (앱 화면은 index.html의 noindex로 검색에서 뺀다).
 * 같은 주소가 로그인 여부로 다른 파일이 되므로 캐시하지 않는다. 브라우저가 소개 페이지를 들고 있다가
 * 로그인한 뒤 304로 다시 쓰면 앱이 뜨지 않는다.
 * 소개 페이지가 없으면(프론트를 넣지 않은 로컬 bootRun 등) 앱을 준다. 다른 화면 경로는 SpaWebConfig가 맡는다.
 */
@Controller
class RootPageController {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	private static final Resource LANDING = new ClassPathResource("static/landing.html");

	private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

	@GetMapping("/")
	ResponseEntity<Resource> root(@AuthenticationPrincipal LoginUser loginUser) {
		Resource page = loginUser == null && LANDING.exists() ? LANDING : INDEX;
		if (!page.exists()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.VARY, HttpHeaders.COOKIE)
			.contentType(HTML)
			.body(page);
	}
}
