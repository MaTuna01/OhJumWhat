package com.ohjumwhat.place;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 로컬 REST API(https://dapi.kakao.com). 키가 없으면 꺼진 채로 뜬다(서버 시작을 막지 않는다).
 * 사용자가 입력한 값은 URI 템플릿 변수로만 넣어 인코딩하고, 호스트는 고정한다. 로그에는 상태 코드만 남긴다(주소·검색어는 남기지 않는다).
 */
@Slf4j
@Component
class RestKakaoLocal implements KakaoLocal {

	static final String BASE_URL = "https://dapi.kakao.com";

	/** null이면 꺼져 있다. */
	private final RestClient client;

	@Autowired
	RestKakaoLocal(MapsProperties properties) {
		this(properties, RestClient.builder().requestFactory(requestFactory()));
	}

	/** 테스트는 MockRestServiceServer를 묶은 builder를 넘긴다(요청 팩토리를 다시 바꾸지 않는다). */
	RestKakaoLocal(MapsProperties properties, RestClient.Builder builder) {
		String key = properties.kakaoRestKey();
		this.client = key == null ? null
				: builder.baseUrl(BASE_URL).defaultHeader("Authorization", "KakaoAK " + key).build();
	}

	private static JdkClientHttpRequestFactory requestFactory() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(3));
		return factory;
	}

	@Override
	public boolean enabled() {
		return client != null;
	}

	@Override
	public Optional<Coordinate> geocode(String query) {
		if (query == null || query.isBlank()) {
			return Optional.empty();
		}
		AddressResponse address = get("/v2/local/search/address.json", Map.of("query", query, "size", 1),
				AddressResponse.class);
		Optional<Coordinate> found = first(address == null ? null : address.documents())
			.flatMap(d -> coordinate(d.x(), d.y()));
		if (found.isPresent()) {
			return found;
		}
		// 도로명 주소가 아니면(예: 역삼역, 회사 건물 이름) 키워드 검색 첫 결과로 찾는다.
		PlaceResponse keyword = get("/v2/local/search/keyword.json", Map.of("query", query, "size", 1),
				PlaceResponse.class);
		return first(keyword == null ? null : keyword.documents()).flatMap(d -> coordinate(d.x(), d.y()));
	}

	/** params의 값은 모두 템플릿 변수로 넣어 인코딩한다(사용자가 { 같은 글자를 쳐도 그대로 검색어가 된다). */
	private <T> T get(String path, Map<String, Object> params, Class<T> type) {
		if (client == null) {
			throw new PlaceSearchUnavailableException("카카오 로컬 키가 없어요.");
		}
		try {
			return client.get()
				.uri(builder -> {
					builder.path(path);
					params.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
					return builder.build(params);
				})
				.retrieve()
				.body(type);
		}
		catch (RestClientResponseException e) {
			log.warn("카카오 로컬 실패: path={}, status={}", path, e.getStatusCode().value());
			throw new PlaceSearchUnavailableException("카카오 로컬 응답이 실패했어요.");
		}
		catch (RestClientException e) {
			log.warn("카카오 로컬 실패: path={}, error={}", path, e.getClass().getSimpleName());
			throw new PlaceSearchUnavailableException("카카오 로컬에 연결하지 못했어요.");
		}
	}

	private static <T> Optional<T> first(List<T> documents) {
		return documents == null || documents.isEmpty() ? Optional.empty() : Optional.of(documents.getFirst());
	}

	/** 카카오 좌표는 문자열이다(x = 경도, y = 위도). */
	static Optional<Coordinate> coordinate(String x, String y) {
		try {
			return Optional.of(new Coordinate(Double.parseDouble(y), Double.parseDouble(x)));
		}
		catch (NullPointerException | NumberFormatException e) {
			return Optional.empty();
		}
	}

	record AddressResponse(List<AddressDocument> documents) {
	}

	record AddressDocument(String x, String y) {
	}

	record PlaceResponse(Meta meta, List<PlaceDocument> documents) {
	}

	record Meta(@JsonProperty("is_end") boolean isEnd) {
	}

	/** 장소 하나. 이름·주소·좌표는 화면에 보여줄 때만 쓰고 저장하지 않는다. */
	record PlaceDocument(String id, @JsonProperty("place_name") String placeName,
			@JsonProperty("category_name") String categoryName,
			@JsonProperty("road_address_name") String roadAddressName,
			@JsonProperty("address_name") String addressName, String x, String y, String distance) {
	}
}
