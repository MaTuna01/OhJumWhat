package com.ohjumwhat.push;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 설정이 온전하면 Firebase로 보내고, 아니면 푸시를 끈다. 어떤 설정 오류도 서버 시작을 막지 않는다
 * (시작 실패 = 헬스 체크 실패 = 서비스 중단). 로그에는 비어 있는 키 이름과 오류 종류만 남기고 값은 남기지 않는다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class PushSenderConfiguration {

	@Bean
	PushSender pushSender(PushProperties properties) {
		List<String> missing = properties.missingKeys();
		if (!missing.isEmpty()) {
			log.warn("웹 푸시 설정이 비어 있어 푸시를 끕니다: 비어 있는 키={}", missing);
			return new DisabledPushSender();
		}
		try {
			FirebasePushSender sender = FirebasePushSender.create(properties);
			log.info("웹 푸시를 켰습니다.");
			return sender;
		}
		catch (Exception | LinkageError e) {
			// 예외 메시지에 설정 값의 일부가 들어갈 수 있어 종류만 남긴다.
			log.warn("웹 푸시를 초기화하지 못해 푸시를 끕니다: 오류={}", e.getClass().getSimpleName());
			return new DisabledPushSender();
		}
	}
}
