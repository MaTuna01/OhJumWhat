package com.ohjumwhat.push;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;

/**
 * Firebase Admin SDK(FCM HTTP v1)로 보낸다. 기기는 토큰이 아니라 Firebase 설치 ID(FID)로 가리킨다.
 * 다른 Firebase 앱(기본 앱)과 섞이지 않게 이름 붙인 FirebaseApp을 쓰고, 서버가 멈출 때 지운다(close).
 */
class FirebasePushSender implements PushSender, AutoCloseable {

	static final String APP_NAME = "ohjumwhat-push";

	static final int CONNECT_TIMEOUT_MS = 3_000;

	static final int READ_TIMEOUT_MS = 5_000;

	/** 기기가 꺼져 있으면 FCM이 하루까지 들고 있다가 전한다(그보다 오래된 알림은 의미가 없다). */
	static final String TTL_SECONDS = "86400";

	private final FirebaseApp app;

	private final FirebaseMessaging messaging;

	private FirebasePushSender(FirebaseApp app, FirebaseMessaging messaging) {
		this.app = app;
		this.messaging = messaging;
	}

	/**
	 * 설정으로 만든다. 네트워크는 쓰지 않는다(인증 토큰은 처음 보낼 때 받는다).
	 *
	 * @param properties 모든 값이 채워진 설정
	 * @throws IOException 서비스 계정 JSON을 읽지 못했다
	 * @throws IllegalArgumentException base64가 아니다
	 * @throws IllegalStateException 서비스 계정의 프로젝트가 웹 설정과 다르다(다른 프로젝트의 FID로 보내면 모두 실패하고,
	 * SENDER_ID_MISMATCH로 기기를 지우게 된다)
	 */
	static FirebasePushSender create(PushProperties properties) throws IOException {
		byte[] json = Base64.getDecoder().decode(properties.serviceAccount().replaceAll("\\s", ""));
		ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(json));
		String projectId = properties.web().projectId();
		if (!projectId.equals(credentials.getProjectId())) {
			throw new IllegalStateException("서비스 계정의 프로젝트가 웹 설정(project-id)과 달라요.");
		}
		FirebaseOptions options = FirebaseOptions.builder()
			.setCredentials(credentials)
			.setProjectId(projectId)
			.setConnectTimeout(CONNECT_TIMEOUT_MS)
			.setReadTimeout(READ_TIMEOUT_MS)
			.build();
		FirebaseApp app = FirebaseApp.initializeApp(options, APP_NAME);
		try {
			return new FirebasePushSender(app, FirebaseMessaging.getInstance(app));
		}
		catch (RuntimeException | LinkageError e) {
			app.delete();
			throw e;
		}
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public Result send(List<String> fids, PushMessage message) {
		BatchResponse response;
		try {
			response = messaging.sendEachForMulticast(multicast(fids, message));
		}
		catch (FirebaseMessagingException e) {
			throw new SendFailedException(String.valueOf(
					e.getMessagingErrorCode() != null ? e.getMessagingErrorCode() : e.getErrorCode()));
		}
		List<MessagingErrorCode> errors = new ArrayList<>();
		for (SendResponse each : response.getResponses()) {
			errors.add(each.isSuccessful() || each.getException() == null ? null
					: each.getException().getMessagingErrorCode());
		}
		return new Result(response.getSuccessCount(), staleFids(fids, errors));
	}

	/**
	 * data만 보낸다(서비스 워커가 알림을 그린다). Web Push 헤더: TTL 하루, Urgency high(절전 중에도 바로),
	 * Topic = 알림 묶음(전달되지 않고 기다리는 같은 종류의 푸시는 새 것으로 바뀐다).
	 */
	static MulticastMessage multicast(List<String> fids, PushMessage message) {
		return MulticastMessage.builder()
			.addAllFids(fids)
			.putAllData(message.data())
			.setWebpushConfig(WebpushConfig.builder()
				.putHeader("TTL", TTL_SECONDS)
				.putHeader("Urgency", "high")
				.putHeader("Topic", message.kind().tag())
				.build())
			.build();
	}

	/**
	 * 더는 받을 수 없는 기기: 구독이 해제됐거나(UNREGISTERED) 다른 프로젝트의 기기다(SENDER_ID_MISMATCH).
	 * 그 밖의 실패(일시 장애·한도 초과 등)는 지우지 않는다.
	 *
	 * @param errors fids와 같은 순서의 결과(성공이면 null)
	 */
	static List<String> staleFids(List<String> fids, List<MessagingErrorCode> errors) {
		List<String> stale = new ArrayList<>();
		for (int i = 0; i < fids.size() && i < errors.size(); i++) {
			if (isStale(errors.get(i))) {
				stale.add(fids.get(i));
			}
		}
		return stale;
	}

	static boolean isStale(MessagingErrorCode code) {
		return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH;
	}

	@Override
	public void close() {
		app.delete();
	}
}
