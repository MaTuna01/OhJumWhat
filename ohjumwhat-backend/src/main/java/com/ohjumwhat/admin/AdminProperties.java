package com.ohjumwhat.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 계정 이메일(ohjumwhat.admin.emails, 환경변수 ADMIN_EMAILS, 쉼표로 여러 개).
 * 저장소가 공개라 이메일은 코드에 두지 않고 서버 .env로만 넣는다.
 */
@ConfigurationProperties("ohjumwhat.admin")
public record AdminProperties(List<String> emails) {

	public AdminProperties {
		emails = emails == null ? List.of()
				: emails.stream().map(String::strip).filter(e -> !e.isEmpty()).map(AdminProperties::normalize).toList();
	}

	public boolean isAdminEmail(String email) {
		return email != null && emails.contains(normalize(email.strip()));
	}

	private static String normalize(String email) {
		return email.toLowerCase(Locale.ROOT);
	}
}
