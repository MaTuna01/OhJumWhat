package com.ohjumwhat.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ohjumwhat.place.MapsProperties;
import com.ohjumwhat.place.PlaceSearchService;
import com.ohjumwhat.push.PushProperties;
import com.ohjumwhat.push.PushSender;

/** 화면이 실행 중에 알아야 하는 설정. 프론트 빌드에는 키를 넣지 않고 여기서 받는다. */
@RestController
class ConfigController {

	private final MapsProperties mapsProperties;

	private final PlaceSearchService placeSearchService;

	private final PushSender pushSender;

	private final PushProperties pushProperties;

	ConfigController(MapsProperties mapsProperties, PlaceSearchService placeSearchService, PushSender pushSender,
			PushProperties pushProperties) {
		this.mapsProperties = mapsProperties;
		this.placeSearchService = placeSearchService;
		this.pushSender = pushSender;
		this.pushProperties = pushProperties;
	}

	@GetMapping("/api/config")
	ConfigResponse config() {
		return new ConfigResponse(mapsProperties.naverMapKeyId(), placeSearchService.enabled(),
				pushSender.enabled() ? pushProperties.web() : null);
	}

	/**
	 * @param naverMapKeyId 네이버 지도 Client ID(공개 값, 등록한 도메인에서만 동작). 없으면 null(지도를 보여주지 않는다)
	 * @param placeSearch 카카오 로컬로 위치를 찾을 수 있는지(지도·거리·근처 식당 찾기)
	 * @param push 웹 푸시(FCM)의 공개 값(apiKey·projectId·appId·messagingSenderId·vapidKey). 푸시가 꺼져 있으면 null
	 * (화면이 알림 설정을 숨긴다)
	 */
	record ConfigResponse(String naverMapKeyId, boolean placeSearch, PushProperties.Web push) {
	}
}
