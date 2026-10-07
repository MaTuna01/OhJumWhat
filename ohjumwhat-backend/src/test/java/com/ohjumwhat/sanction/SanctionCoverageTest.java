package com.ohjumwhat.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.ohjumwhat.IntegrationTest;

/**
 * 새 쓰기 API를 만들 때 제재로 막을지 정하지 않고 지나가지 않게 한다: /api/**(관리자 API 제외)의 GET이 아닌 핸들러는 모두
 * {@link Restricted}나 {@link Unrestricted} 중 정확히 하나를 단다. 막는 API는 모두 SanctionRestrictionIntegrationTest의
 * 표에 있어야 한다(종류별 개수로 확인).
 */
class SanctionCoverageTest extends IntegrationTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	void 사용자_쓰기_API는_모두_막을지_정했다() {
		List<String> missing = new ArrayList<>();
		List<String> misplaced = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
			Set<String> patterns = entry.getKey().getPatternValues();
			Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
			HandlerMethod handler = entry.getValue();
			int marks = (handler.hasMethodAnnotation(Restricted.class) ? 1 : 0)
					+ (handler.hasMethodAnnotation(Unrestricted.class) ? 1 : 0);
			boolean userApi = patterns.stream().anyMatch(p -> p.startsWith("/api/") && !p.startsWith("/api/admin/"));
			boolean write = methods.isEmpty() || !methods.equals(Set.of(RequestMethod.GET));
			if (userApi && write) {
				if (marks != 1) {
					missing.add(methods + " " + patterns + " " + handler.getShortLogMessage());
				}
			}
			else if (marks != 0) {
				// 인터셉터가 보지 않는 핸들러(GET, 관리자 API)에 달면 막는다고 오해하게 된다.
				misplaced.add(methods + " " + patterns + " " + handler.getShortLogMessage());
			}
		}
		assertThat(missing).as("@Restricted나 @Unrestricted 중 하나를 달아야 하는 핸들러").isEmpty();
		assertThat(misplaced).as("GET·관리자 API에는 달지 않는다").isEmpty();
	}

	@Test
	void 막는_API는_모두_제한_표에서_확인한다() {
		Map<Restriction, Long> annotated = handlerMapping.getHandlerMethods()
			.values()
			.stream()
			.filter(handler -> handler.hasMethodAnnotation(Restricted.class))
			.collect(Collectors.groupingBy(handler -> handler.getMethodAnnotation(Restricted.class).value(),
					Collectors.counting()));
		Map<Restriction, Long> tested = SanctionRestrictionIntegrationTest.restrictedEndpoints()
			.collect(Collectors.groupingBy(SanctionRestrictionIntegrationTest.Endpoint::type, Collectors.counting()));

		assertThat(tested).isEqualTo(annotated);
		// 계약서의 대상: 조직 4, 투표·메뉴·식당·정기 투표·댓글 11, 채팅 2, 쪽지 2, 방명록 1, 프로필 5
		assertThat(annotated).containsExactlyInAnyOrderEntriesOf(Map.of(Restriction.SUSPEND, 4L, Restriction.POLL, 11L,
				Restriction.CHAT, 2L, Restriction.LETTER, 2L, Restriction.GUESTBOOK, 1L, Restriction.PROFILE, 5L));
	}
}
