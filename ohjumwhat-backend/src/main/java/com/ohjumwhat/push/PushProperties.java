package com.ohjumwhat.push;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시(FCM) 설정(ohjumwhat.push). 하나라도 비면 푸시를 끈다(PushSenderConfiguration). 값은 로그에 남기지 않는다.
 *
 * @param web 화면이 Firebase SDK를 초기화할 공개 값. GET /api/config로 준다
 * @param serviceAccount 서비스 계정 JSON을 base64 한 줄로(환경변수 FIREBASE_SERVICE_ACCOUNT_BASE64). 비밀이라 서버에서만 쓴다
 */
@ConfigurationProperties("ohjumwhat.push")
public record PushProperties(Web web, String serviceAccount) {

	public PushProperties {
		web = web == null ? new Web(null, null, null, null, null) : web;
		serviceAccount = blankToNull(serviceAccount);
	}

	/** 서비스 계정(비밀)은 가린다(설정 객체가 로그·오류 메시지·actuator에 찍혀도 새지 않게). */
	@Override
	public String toString() {
		return "PushProperties[web=" + web + ", serviceAccount=" + (serviceAccount == null ? "null" : "****") + "]";
	}

	/** 비어 있는 설정 키 이름(값이 아니라 이름만). 모두 채워졌으면 빈 목록 */
	List<String> missingKeys() {
		List<String> missing = new ArrayList<>(web.missingKeys());
		if (serviceAccount == null) {
			missing.add("service-account");
		}
		return missing;
	}

	/**
	 * 화면이 쓰는 공개 값(환경변수 FIREBASE_*). Firebase 콘솔의 웹 앱 설정과 Cloud Messaging의 웹 푸시 인증서(VAPID 공개 키)다.
	 */
	public record Web(String apiKey, String projectId, String appId, String messagingSenderId, String vapidKey) {

		public Web {
			apiKey = blankToNull(apiKey);
			projectId = blankToNull(projectId);
			appId = blankToNull(appId);
			messagingSenderId = blankToNull(messagingSenderId);
			vapidKey = blankToNull(vapidKey);
		}

		List<String> missingKeys() {
			List<String> missing = new ArrayList<>();
			if (apiKey == null) {
				missing.add("web.api-key");
			}
			if (projectId == null) {
				missing.add("web.project-id");
			}
			if (appId == null) {
				missing.add("web.app-id");
			}
			if (messagingSenderId == null) {
				missing.add("web.messaging-sender-id");
			}
			if (vapidKey == null) {
				missing.add("web.vapid-key");
			}
			return missing;
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
