package com.ohjumwhat;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ohjumwhat.place.NaverShortLinks;

/** 테스트에서 실제 naver.me로 요청하지 않도록 정해진 표로 답한다. 표에 없는 코드는 확인 실패(빈 값), GONE_CODE는 없는 코드(404)다. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeNaverShortLinksConfiguration {

	/** 네이버 지도 장소 공유 링크(장소 ID 1868364770) */
	public static final String PLACE_CODE = "place123";

	/** 즐겨찾기 폴더 공유 링크(장소 ID 없음) */
	public static final String FAVORITE_CODE = "favor123";

	/** 네이버가 아닌 곳으로 보내는 링크 */
	public static final String OTHER_HOST_CODE = "other123";

	/** naver.me에 없는 코드(404). 표에 없는 다른 코드는 확인 실패(빈 값)다. */
	public static final String GONE_CODE = "gone1234";

	private static final Map<String, String> LOCATIONS = Map.of(
			PLACE_CODE, "https://m.place.naver.com/share?id=1868364770&tabsPath=%2Fhome&appMode=detail",
			FAVORITE_CODE, "https://map.naver.com/v5/favorite/myPlace/folder/abc123",
			OTHER_HOST_CODE, "https://evil.example.com/place/1868364770");

	@Bean
	@Primary
	NaverShortLinks fakeNaverShortLinks() {
		return code -> {
			if (GONE_CODE.equals(code)) {
				throw new NaverShortLinks.NotFound();
			}
			return Optional.ofNullable(LOCATIONS.get(code));
		};
	}
}
