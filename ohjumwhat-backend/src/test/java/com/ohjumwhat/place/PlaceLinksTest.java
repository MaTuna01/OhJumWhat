package com.ohjumwhat.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.common.ApiException;

class PlaceLinksTest {

	private static final String PLACE = "https://map.naver.com/p/entry/place/1868364770";

	@Test
	void 지도_앱_공유_문구에서_링크만_꺼낸다() {
		assertThat(PlaceLinks.normalize("[네이버 지도]\n김밥천국 강남점\nhttps://naver.me/5abcDEF"))
			.isEqualTo("https://naver.me/5abcDEF");
		assertThat(PlaceLinks.normalize("[카카오맵] 김밥천국 https://kko.to/AbC123")).isEqualTo("https://kko.to/AbC123");
	}

	@Test
	void 공유_문구의_네이버_링크는_스킴이_없어도_된다() {
		assertThat(PlaceLinks.normalize("[네이버지도]\n할매집\nnaver.me/5abcDEF")).isEqualTo("https://naver.me/5abcDEF");
		assertThat(PlaceLinks.normalize("naver.me/5abcDEF에서 봐요")).isEqualTo("https://naver.me/5abcDEF");
		assertThat(PlaceLinks.normalize("[네이버 지도]\n할매집\nmap.naver.com/p/entry/place/1868364770")).isEqualTo(PLACE);
	}

	@Test
	void 링크가_여럿이면_네이버_지도_링크를_먼저_쓴다() {
		assertThat(PlaceLinks.normalize("후기 https://blog.example.com/a 지도 https://naver.me/abcd1234"))
			.isEqualTo("https://naver.me/abcd1234");
	}

	@Test
	void 장소_ID가_있는_네이버_링크는_정식_링크로_바꾼다() {
		assertThat(PlaceLinks.normalize("https://map.naver.com/p/entry/place/1868364770?c=15.00,0,0,0,dh")).isEqualTo(PLACE);
		assertThat(PlaceLinks.normalize("https://map.naver.com/v5/entry/place/1868364770")).isEqualTo(PLACE);
		assertThat(PlaceLinks.normalize(
				"https://map.naver.com/p/search/김치찌개/place/1868364770?c=15.00,0,0,0,dh&placePath=%2Fhome"))
			.isEqualTo(PLACE);
		assertThat(PlaceLinks.normalize("https://m.place.naver.com/restaurant/1868364770/home")).isEqualTo(PLACE);
		assertThat(PlaceLinks.normalize("https://pcmap.place.naver.com/restaurant/1868364770/home?from=map"))
			.isEqualTo(PLACE);
		assertThat(PlaceLinks.normalize("https://m.place.naver.com/share?id=1868364770&tabsPath=%2Fhome"))
			.isEqualTo(PLACE);
	}

	@Test
	void 장소_ID가_없는_네이버_지도_링크는_그대로_둔다() {
		assertThat(PlaceLinks.normalize("https://map.naver.com/p/search/%EA%B9%80%EC%B9%98"))
			.isEqualTo("https://map.naver.com/p/search/%EA%B9%80%EC%B9%98");
		assertThat(PlaceLinks.normalize("https://map.naver.com/p/entry/place/abc"))
			.isEqualTo("https://map.naver.com/p/entry/place/abc");
	}

	@Test
	void 길이는_정식_링크로_바꾼_뒤에_검사한다() {
		String longSearch = "https://map.naver.com/p/search/" + "a".repeat(600) + "/place/1868364770?c=1";
		assertThat(PlaceLinks.normalize(longSearch)).isEqualTo(PLACE);
		assertThatThrownBy(() -> PlaceLinks.normalize("https://" + "a".repeat(500) + ".com"))
			.isInstanceOf(ApiException.class)
			.hasMessage("링크는 500자 이하로 입력해 주세요.");
	}

	@Test
	void 스킴이_없으면_https를_붙이고_끝_문장부호는_뺀다() {
		assertThat(PlaceLinks.normalize("  map.kakao.com/?itemId=123 ")).isEqualTo("https://map.kakao.com/?itemId=123");
		assertThat(PlaceLinks.normalize("HTTP://Maps.Google.com/x")).isEqualTo("HTTP://Maps.Google.com/x");
		assertThat(PlaceLinks.normalize("여기(https://map.kakao.com/123).")).isEqualTo("https://map.kakao.com/123");
		assertThat(PlaceLinks.normalize("   ")).isNull();
		assertThat(PlaceLinks.normalize(null)).isNull();
	}

	@Test
	void http_https_주소가_아니면_거절한다() {
		assertThatThrownBy(() -> PlaceLinks.normalize("javascript:alert(1)")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> PlaceLinks.normalize("김밥천국")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> PlaceLinks.normalize("ftp://example.com/a")).isInstanceOf(ApiException.class);
	}

	@Test
	void 단축_링크_코드와_리디렉션_주소의_장소를_꺼낸다() {
		assertThat(PlaceLinks.naverShortCode("https://naver.me/5abcDEF")).contains("5abcDEF");
		assertThat(PlaceLinks.naverShortCode("https://map.naver.com/p/entry/place/1")).isEmpty();
		assertThat(PlaceLinks.naverPlaceUrl("https://m.place.naver.com/share?id=1868364770&tabsPath=%2Fhome&appMode=detail"))
			.contains(PLACE);
		assertThat(PlaceLinks.naverPlaceUrl("https://map.naver.com/v5/favorite/myPlace/folder/abc123")).isEmpty();
		assertThat(PlaceLinks.naverPlaceUrl("https://evil.example.com/place/123")).isEmpty();
	}

	@Test
	void 식당_이름은_공백을_정리하고_100자까지다() {
		assertThat(PlaceLinks.name("  할매집\n  강남점 ")).isEqualTo("할매집 강남점");
		assertThat(PlaceLinks.name("   ")).isNull();
		assertThat(PlaceLinks.name(null)).isNull();
		assertThat(PlaceLinks.name("가".repeat(100))).hasSize(100);
		assertThatThrownBy(() -> PlaceLinks.name("가".repeat(101)))
			.isInstanceOf(ApiException.class)
			.hasMessage("식당 이름은 100자 이하로 입력해 주세요.");
	}
}
