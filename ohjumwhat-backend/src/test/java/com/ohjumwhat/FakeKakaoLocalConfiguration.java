package com.ohjumwhat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ohjumwhat.place.Coordinate;
import com.ohjumwhat.place.KakaoLocal;
import com.ohjumwhat.place.KakaoPlace;
import com.ohjumwhat.place.PlaceSearchUnavailableException;

/** 테스트에서 실제 카카오 로컬로 요청하지 않도록 정해진 표로 답한다. 표에 없는 주소·검색어는 못 찾음(빈 값)이다. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeKakaoLocalConfiguration {

	/** 회사 주소(역삼역 근처) */
	public static final String OFFICE_ADDRESS = "서울 강남구 테헤란로 152";

	public static final Coordinate OFFICE = new Coordinate(37.5000, 127.0364);

	/** 식당 주소(회사에서 약 350m) */
	public static final String PLACE_ADDRESS = "서울 강남구 테헤란로 1";

	public static final Coordinate PLACE = new Coordinate(37.4980, 127.0330);

	/** 이 주소나 검색어로 찾으면 카카오 장애처럼 실패한다. */
	public static final String FAILING_ADDRESS = "카카오 장애 주소";

	public static final String FAILING_QUERY = "장애";

	/** 「김치찌개」: 이름에 검색어가 있는 식당(1페이지) */
	public static final KakaoPlace HALMAE = new KakaoPlace("1001", "할매집 김치찌개", List.of("한식", "찌개,전골"),
			"서울 강남구 테헤란로 10", new Coordinate(37.4990, 127.0350), 150);

	/** 「김치찌개」: 메뉴로만 걸린 식당(1페이지, 정확도순으로 앞) */
	public static final KakaoPlace MYEONGDONG = new KakaoPlace("1002", "명동교자", List.of("한식", "국수", "칼국수"),
			"서울 강남구 역삼로 5", new Coordinate(37.5040, 127.0400), 600);

	/** 「김치찌개」의 2페이지에만 나오는 식당(분류가 체인 이름) */
	public static final KakaoPlace FAR = new KakaoPlace("1003", "김치찌개랑 역삼점", List.of("한식", "찌개,전골", "김치찌개랑"),
			"서울 강남구 논현로 9", new Coordinate(37.5100, 127.0300), 1200);

	/** 조직 반경으로 다시 찾으면 안 나오고, 넓은 반경(20km) 가까운 순에서만 나오는 식당 */
	public static final KakaoPlace WIDE_ONLY = new KakaoPlace("1004", "멀리김치찌개", List.of("한식", "찌개,전골"),
			"서울 강남구 도산대로 1", new Coordinate(37.5200, 127.0300), 2500);

	private static final Map<String, Coordinate> COORDINATES = Map.of(OFFICE_ADDRESS, OFFICE, PLACE_ADDRESS, PLACE);

	@Bean
	@Primary
	FakeKakaoLocal fakeKakaoLocal() {
		return new FakeKakaoLocal();
	}

	/** 부른 횟수와 마지막 검색 반경을 기억한다(멤버가 아니면 부르지 않는지, 다시 찾을 때 반경 확인용). */
	public static class FakeKakaoLocal implements KakaoLocal {

		private final AtomicInteger calls = new AtomicInteger();

		private volatile int lastRadius;

		private volatile Sort lastSort;

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public Optional<Coordinate> geocode(String query) {
			calls.incrementAndGet();
			if (FAILING_ADDRESS.equals(query)) {
				throw new PlaceSearchUnavailableException("테스트 장애");
			}
			return Optional.ofNullable(COORDINATES.get(query));
		}

		/**
		 * 「김치찌개」 정확도순: 1페이지 명동교자·할매집, 2페이지 김치찌개랑. 넓은 반경(20km)을 가까운 순으로 보면 멀리김치찌개도 나온다.
		 * 검색어가 없으면(둘러보기) 할매집·명동교자·김치찌개랑 한 페이지
		 */
		@Override
		public KakaoPlace.Page searchRestaurants(String query, Coordinate center, int radius, Sort sort, int page) {
			calls.incrementAndGet();
			lastRadius = radius;
			lastSort = sort;
			if (FAILING_QUERY.equals(query)) {
				throw new PlaceSearchUnavailableException("테스트 장애");
			}
			if (query == null) {
				return new KakaoPlace.Page(page == 1 ? List.of(HALMAE, MYEONGDONG, FAR) : List.of(), true);
			}
			if ("김치찌개".equals(query) && radius >= 20_000 && sort == Sort.DISTANCE) {
				return new KakaoPlace.Page(page == 1 ? List.of(HALMAE, MYEONGDONG, FAR, WIDE_ONLY) : List.of(), true);
			}
			if ("김치찌개".equals(query)) {
				return page == 1 ? new KakaoPlace.Page(List.of(MYEONGDONG, HALMAE), false)
						: new KakaoPlace.Page(page == 2 ? List.of(FAR) : List.of(), true);
			}
			return new KakaoPlace.Page(List.of(), true);
		}

		public int calls() {
			return calls.get();
		}

		public int lastRadius() {
			return lastRadius;
		}

		public Sort lastSort() {
			return lastSort;
		}

		public void reset() {
			calls.set(0);
		}
	}
}
