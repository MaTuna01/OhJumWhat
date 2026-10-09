package com.ohjumwhat.loadtest;

import java.time.Instant;
import java.util.List;

/**
 * 시드 결과. 부하 도구(k6)가 읽는 파일의 형식이라, 필드를 바꾸면 loadtest/lib/seed.js도 함께 고친다.
 *
 * @param generatedAt 만든 시각
 * @param closesAt 모든 투표의 마감 시각(시나리오가 마감 기준 상대 시각을 계산한다)
 * @param orgs 조직마다 투표 하나와 멤버 목록
 */
public record SeedResult(Instant generatedAt, Instant closesAt, List<Org> orgs) {

	/**
	 * @param optionIds 투표의 메뉴 ID(추가한 순서)
	 * @param members 멤버마다 로그인 세션 하나
	 */
	public record Org(long orgId, String name, long pollId, List<Long> optionIds, List<Member> members) {
	}

	/**
	 * @param sessionCookie 브라우저가 보내는 SESSION 쿠키 값(세션 ID의 base64)
	 */
	public record Member(long userId, String googleSub, String sessionCookie) {
	}

	public int memberCount() {
		return orgs.stream().mapToInt(org -> org.members().size()).sum();
	}
}
