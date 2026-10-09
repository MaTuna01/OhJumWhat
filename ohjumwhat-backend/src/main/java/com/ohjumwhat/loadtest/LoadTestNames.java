package com.ohjumwhat.loadtest;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 부하 테스트 데이터를 알아보는 규칙의 원본. 시드가 만드는 회원·조직은 모두 이 접두어를 갖고, 정리는 이 접두어로만 찾는다.
 * 접두어 밖의 데이터(실제 회원·조직)는 시드도 정리도 건드리지 않는다.
 */
public final class LoadTestNames {

	/** users.google_sub 접두어. 실제 구글 sub는 숫자라 겹치지 않는다. */
	public static final String SUB_PREFIX = "loadtest-";

	/** users.email 도메인. .invalid는 예약된 최상위 도메인이라 실제 메일 주소가 될 수 없다. */
	public static final String EMAIL_DOMAIN = "@loadtest.invalid";

	/** organizations.name 접두어. 화면·관리자 콘솔에서 바로 알아볼 수 있다. */
	public static final String ORG_PREFIX = "[부하테스트] ";

	/** polls.title 접두어 */
	public static final String POLL_PREFIX = "[부하테스트] ";

	private static final SecureRandom RANDOM = new SecureRandom();

	private LoadTestNames() {
	}

	/** 조직 번호와 멤버 번호로 google sub를 만든다(예: loadtest-3-12). */
	public static String googleSub(int org, int member) {
		return SUB_PREFIX + org + "-" + member;
	}

	public static String email(int org, int member) {
		return googleSub(org, member) + EMAIL_DOMAIN;
	}

	/** 화면 명단에 보이는 이름 */
	public static String userName(int org, int member) {
		return "부하테스트 " + org + "-" + member;
	}

	public static String organizationName(int org) {
		return ORG_PREFIX + "조직 " + org;
	}

	/** 초대 링크용 토큰. OrganizationService.newInviteToken과 같은 방식(32바이트 난수의 URL-safe base64, 43자). */
	static String inviteToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SQL LIKE 패턴: 부하 테스트 회원의 google_sub */
	static String subPattern() {
		return SUB_PREFIX + "%";
	}

	/** SQL LIKE 패턴: 부하 테스트 회원의 email */
	static String emailPattern() {
		return "%" + EMAIL_DOMAIN;
	}

	/** SQL LIKE 패턴: 부하 테스트 조직 이름 */
	static String organizationPattern() {
		return ORG_PREFIX + "%";
	}
}
