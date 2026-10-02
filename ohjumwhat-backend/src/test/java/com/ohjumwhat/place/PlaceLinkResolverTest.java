package com.ohjumwhat.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.common.ApiException;

class PlaceLinkResolverTest {

	private static final String PLACE = "https://map.naver.com/p/entry/place/1868364770";

	private final List<String> requested = new ArrayList<>();

	private final PlaceLinkResolver resolver = new PlaceLinkResolver(code -> {
		requested.add(code);
		if (code.equals("gone1234")) {
			throw new NaverShortLinks.NotFound();
		}
		return Optional.ofNullable(Map.of(
				"place123", "https://m.place.naver.com/share?id=1868364770&tabsPath=%2Fhome",
				"relative1", "/share?id=1868364770",
				"favor123", "https://map.naver.com/v5/favorite/myPlace/folder/abc123",
				"other123", "https://evil.example.com/place/1868364770").get(code));
	});

	@Test
	void naver_me_링크는_리디렉션_주소의_장소_ID로_정식_링크를_만든다() {
		assertThat(resolver.resolve("[네이버 지도]\n할매집\nnaver.me/place123")).isEqualTo(PLACE);
		assertThat(requested).containsExactly("place123");
	}

	@Test
	void 확인하지_못하면_단축_링크를_그대로_둔다() {
		assertThat(resolver.resolve("https://naver.me/unknown1")).isEqualTo("https://naver.me/unknown1");
		assertThat(resolver.resolve("https://naver.me/favor123")).isEqualTo("https://naver.me/favor123");
		assertThat(resolver.resolve("https://naver.me/relative1")).isEqualTo("https://naver.me/relative1");
	}

	@Test
	void naver_me에_없는_코드면_잘못_붙인_링크라_거절한다() {
		assertThatThrownBy(() -> resolver.resolve("[네이버 지도]\n할매집\nhttps://naver.me/gone1234"))
			.isInstanceOf(ApiException.class)
			.hasMessage("공유 링크를 찾을 수 없어요. 네이버 지도에서 링크를 다시 복사해 주세요.");
	}

	@Test
	void 네이버가_아닌_곳으로_보내는_리디렉션은_믿지_않는다() {
		assertThat(resolver.resolve("https://naver.me/other123")).isEqualTo("https://naver.me/other123");
	}

	@Test
	void 단축_링크가_아니면_요청하지_않는다() {
		assertThat(resolver.resolve("https://map.naver.com/p/entry/place/1868364770?c=1")).isEqualTo(PLACE);
		assertThat(resolver.resolve("https://kko.to/AbC123")).isEqualTo("https://kko.to/AbC123");
		assertThat(resolver.resolve("  ")).isNull();
		assertThat(requested).isEmpty();
	}

	@Test
	void 링크가_없으면_식당도_없고_이름이_잘못되면_요청_전에_거절한다() {
		assertThat(resolver.place(null, "할매집")).isNull();
		assertThat(resolver.place("naver.me/place123", "  할매집 ")).isEqualTo(new PlaceLink(PLACE, "할매집"));
		assertThatThrownBy(() -> resolver.place("naver.me/place123", "가".repeat(101))).isInstanceOf(ApiException.class);
		assertThat(requested).containsExactly("place123");
	}

	@Test
	void 상대_Location은_naver_me_기준으로_푼다() {
		assertThat(JdkNaverShortLinks.absolute("/share?id=1")).contains("https://naver.me/share?id=1");
		assertThat(JdkNaverShortLinks.absolute("https://m.place.naver.com/share?id=1")).contains("https://m.place.naver.com/share?id=1");
		assertThat(JdkNaverShortLinks.absolute("ht tp://bad")).isEmpty();
	}
}
