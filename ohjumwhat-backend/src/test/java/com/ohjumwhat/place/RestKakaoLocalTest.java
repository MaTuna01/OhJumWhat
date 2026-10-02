package com.ohjumwhat.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestKakaoLocalTest {

	MockRestServiceServer server;

	RestKakaoLocal kakao;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		kakao = new RestKakaoLocal(new MapsProperties("test-key", null), builder);
	}

	@Test
	void 주소를_좌표로_바꾸고_키를_헤더에_붙인다() {
		server.expect(requestTo(startsWith("/v2/local/search/address.json")))
			.andExpect(header("Authorization", "KakaoAK test-key"))
			.andExpect(queryParam("query", encoded("서울 강남구 테헤란로 152")))
			.andRespond(withSuccess("""
					{"meta": {"total_count": 1}, "documents": [{"address_name": "서울 강남구 역삼동 737",
					"x": "127.0364", "y": "37.5000", "road_address": {"address_name": "서울 강남구 테헤란로 152"}}]}""",
					MediaType.APPLICATION_JSON));

		assertThat(kakao.geocode("서울 강남구 테헤란로 152")).contains(new Coordinate(37.5000, 127.0364));
		server.verify();
	}

	@Test
	void 주소로_못_찾으면_키워드_검색_첫_결과를_쓴다() {
		server.expect(requestTo(startsWith("/v2/local/search/address.json")))
			.andRespond(withSuccess("{\"documents\": []}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith("/v2/local/search/keyword.json")))
			.andExpect(queryParam("query", encoded("역삼역 {2호선}")))
			.andRespond(withSuccess("""
					{"meta": {"is_end": true}, "documents": [{"id": "21160803", "place_name": "역삼역 2호선",
					"category_name": "교통,수송 > 지하철,전철", "x": "127.03646", "y": "37.50065", "distance": ""}]}""",
					MediaType.APPLICATION_JSON));

		assertThat(kakao.geocode("역삼역 {2호선}")).contains(new Coordinate(37.50065, 127.03646));
		server.verify();
	}

	@Test
	void 둘_다_없으면_빈_값이다() {
		server.expect(requestTo(startsWith("/v2/local/search/address.json")))
			.andRespond(withSuccess("{\"documents\": []}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith("/v2/local/search/keyword.json")))
			.andRespond(withSuccess("{\"documents\": []}", MediaType.APPLICATION_JSON));

		assertThat(kakao.geocode("없는 주소")).isEmpty();
	}

	@Test
	void 검색어가_있으면_키워드로_근처_음식점을_거리순으로_찾는다() {
		server.expect(requestTo(startsWith("/v2/local/search/keyword.json")))
			.andExpect(queryParam("query", encoded("김치찌개")))
			.andExpect(queryParam("category_group_code", "FD6"))
			.andExpect(queryParam("x", "127.0364"))
			.andExpect(queryParam("y", "37.5"))
			.andExpect(queryParam("radius", "20000"))
			.andExpect(queryParam("sort", "distance"))
			.andExpect(queryParam("page", "2"))
			.andExpect(queryParam("size", "15"))
			.andRespond(withSuccess("""
					{"meta": {"is_end": false, "pageable_count": 45}, "documents": [
					{"id": "1001", "place_name": "할매집", "category_name": "음식점 > 한식 > 찌개,전골",
					"road_address_name": "서울 강남구 테헤란로 10", "address_name": "서울 강남구 역삼동 1",
					"x": "127.035", "y": "37.499", "distance": "150", "place_url": "http://place.map.kakao.com/1001"},
					{"id": "1002", "place_name": "명동교자", "category_name": "음식점",
					"road_address_name": "", "address_name": "서울 강남구 역삼동 2",
					"x": "127.04", "y": "37.504", "distance": ""}]}""", MediaType.APPLICATION_JSON));

		KakaoPlace.Page page = kakao.searchRestaurants("김치찌개", new Coordinate(37.5, 127.0364), 50_000, 2);

		assertThat(page.end()).isFalse();
		assertThat(page.places()).containsExactly(
				new KakaoPlace("1001", "할매집", "찌개,전골", "서울 강남구 테헤란로 10", new Coordinate(37.499, 127.035), 150),
				new KakaoPlace("1002", "명동교자", "음식점", "서울 강남구 역삼동 2", new Coordinate(37.504, 127.04), null));
		server.verify();
	}

	@Test
	void 검색어가_없으면_분류로_근처_음식점을_둘러본다() {
		server.expect(requestTo(startsWith("/v2/local/search/category.json")))
			.andExpect(queryParam("category_group_code", "FD6"))
			.andExpect(queryParam("radius", "1000"))
			.andRespond(withSuccess("{\"meta\": {\"is_end\": true}, \"documents\": []}", MediaType.APPLICATION_JSON));

		assertThat(kakao.searchRestaurants(" ", new Coordinate(37.5, 127.0364), 1000, 1).end()).isTrue();
		server.verify();
	}

	@Test
	void 응답이_실패하면_쓸_수_없다는_예외를_던진다() {
		server.expect(requestTo(startsWith("/v2/local/search/address.json"))).andRespond(withServerError());

		assertThatThrownBy(() -> kakao.geocode("서울 강남구 테헤란로 152"))
			.isInstanceOf(PlaceSearchUnavailableException.class);
	}

	@Test
	void 키가_없으면_꺼져_있고_부르면_예외다() {
		RestKakaoLocal disabled = new RestKakaoLocal(new MapsProperties(" ", null), RestClient.builder());

		assertThat(disabled.enabled()).isFalse();
		assertThatThrownBy(() -> disabled.geocode("서울")).isInstanceOf(PlaceSearchUnavailableException.class);
	}

	private static org.hamcrest.Matcher<String> startsWith(String path) {
		return org.hamcrest.Matchers.startsWith(RestKakaoLocal.BASE_URL + path);
	}

	/** MockRestServiceServer는 인코딩된 쿼리 값을 그대로 비교한다. */
	private static String encoded(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
