package com.ohjumwhat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ohjumwhat.place.Coordinate;
import com.ohjumwhat.place.KakaoLocal;
import com.ohjumwhat.place.PlaceSearchUnavailableException;

/** 테스트에서 실제 카카오 로컬로 요청하지 않도록 정해진 표로 답한다. 표에 없는 주소는 못 찾음(빈 값)이다. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeKakaoLocalConfiguration {

	/** 회사 주소(역삼역 근처) */
	public static final String OFFICE_ADDRESS = "서울 강남구 테헤란로 152";

	public static final Coordinate OFFICE = new Coordinate(37.5000, 127.0364);

	/** 식당 주소(회사에서 약 350m) */
	public static final String PLACE_ADDRESS = "서울 강남구 테헤란로 1";

	public static final Coordinate PLACE = new Coordinate(37.4980, 127.0330);

	/** 이 주소를 찾으면 카카오 장애처럼 실패한다. */
	public static final String FAILING_ADDRESS = "카카오 장애 주소";

	private static final Map<String, Coordinate> COORDINATES = Map.of(OFFICE_ADDRESS, OFFICE, PLACE_ADDRESS, PLACE);

	@Bean
	@Primary
	FakeKakaoLocal fakeKakaoLocal() {
		return new FakeKakaoLocal();
	}

	/** 부른 횟수를 센다(멤버가 아니면 부르지 않는지 확인용). */
	public static class FakeKakaoLocal implements KakaoLocal {

		private final AtomicInteger calls = new AtomicInteger();

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

		public int calls() {
			return calls.get();
		}

		public void reset() {
			calls.set(0);
		}
	}
}
