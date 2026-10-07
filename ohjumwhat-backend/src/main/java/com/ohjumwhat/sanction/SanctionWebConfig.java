package com.ohjumwhat.sanction;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 제재 확인을 사용자 API에 건다. 관리자 API는 제재 대상이 아니다(관리자는 제재할 수 없다). */
@Configuration
class SanctionWebConfig implements WebMvcConfigurer {

	private final SanctionInterceptor sanctionInterceptor;

	SanctionWebConfig(SanctionInterceptor sanctionInterceptor) {
		this.sanctionInterceptor = sanctionInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(sanctionInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/admin/**");
	}
}
