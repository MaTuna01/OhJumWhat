package com.ohjumwhat.common;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 운영에서는 React 빌드 결과가 classpath:/static/에 들어간다.
 * 파일이 없는 화면 경로(/orgs/1 등)는 index.html을 돌려줘 React Router가 처리하게 한다.
 * 컨트롤러가 먼저 매칭되므로 /api 요청에는 영향이 없다.
 * 해시가 없는 파일(index.html, 웹 푸시 서비스 워커 firebase-messaging-sw.js, PWA 매니페스트 manifest.webmanifest 등)은
 * no-cache로 내보내 배포하면 바로 새 파일을 받게 한다.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

	private static final String STATIC = "classpath:/static/";

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Vite가 파일명에 해시를 붙이므로 오래 캐시해도 된다.
		registry.addResourceHandler("/assets/**")
			.addResourceLocations(STATIC + "assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());

		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC)
			.setCacheControl(CacheControl.noCache())
			.resourceChain(true)
			.addResolver(new SpaFallbackResolver());
	}

	/** PWA 매니페스트의 형식. 서블릿 컨테이너의 기본 확장자 표에 없어서 지정한다. */
	@Bean
	WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webManifestMimeMapping() {
		return factory -> factory
			.addMimeMappings(new MimeMappings(Map.of("webmanifest", "application/manifest+json")));
	}

	private static class SpaFallbackResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource resource = location.createRelative(resourcePath);
			if (resource.exists() && resource.isReadable()) {
				return resource;
			}
			// API와 파일(확장자가 있는 경로)은 없으면 그대로 404로 둔다.
			if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
				return null;
			}
			Resource index = new ClassPathResource("static/index.html");
			return index.exists() ? index : null;
		}
	}
}
