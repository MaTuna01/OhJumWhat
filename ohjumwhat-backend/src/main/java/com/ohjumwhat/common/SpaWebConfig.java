package com.ohjumwhat.common;

import java.io.IOException;
import java.time.Duration;

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
