package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.ohjumwhat.FakeKakaoLocalConfiguration;
import com.ohjumwhat.FakeNaverShortLinksConfiguration;
import com.ohjumwhat.TestClockConfiguration;
import com.ohjumwhat.TestcontainersConfiguration;

/**
 * 운영처럼 API 명세를 끄면(SWAGGER_ENABLED가 없으면 application.example.yml의 false) 명세 화면·JSON과 swagger-ui 파일이
 * 하나도 열리지 않는다. 설정이 달라 다른 통합 테스트와 컨텍스트를 함께 쓰지 못하고 따로 띄운다.
 */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, FakeNaverShortLinksConfiguration.class,
		FakeKakaoLocalConfiguration.class })
class ApiDocsDisabledTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Test
	void 명세를_끄면_화면과_JSON과_swagger_ui_파일이_없다() throws Exception {
		assertThat(context.getBeanNamesForType(OpenApiConfig.class)).isEmpty();
		for (String path : new String[] { "/v3/api-docs", "/v3/api-docs/user", "/v3/api-docs/admin",
				"/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html",
				"/webjars/swagger-ui/index.html", "/webjars/swagger-ui/swagger-ui-bundle.js" }) {
			MockHttpServletResponse response = mockMvc.perform(get(path)).andReturn().getResponse();
			// 확장자가 없는 /v3/api-docs 경로는 SPA 화면(없는 화면 = index.html)으로 넘어간다. 명세 JSON·swagger-ui 파일만 아니면 된다.
			assertThat(response.getContentAsString()).as(path).doesNotContain("openapi", "swagger");
		}
	}
}
