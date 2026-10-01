package com.ohjumwhat.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.common.ApiException;

class MenuLinksTest {

	@Test
	void 지도_앱_공유_문구에서_주소만_꺼낸다() {
		assertThat(MenuLinks.normalize("[네이버 지도]\n김밥천국 강남점\nhttps://naver.me/5abcDEF"))
			.isEqualTo("https://naver.me/5abcDEF");
		assertThat(MenuLinks.normalize("[카카오맵] 김밥천국 https://kko.to/AbC123")).isEqualTo("https://kko.to/AbC123");
	}

	@Test
	void 스킴이_없으면_https를_붙이고_빈_값은_링크_없음이다() {
		assertThat(MenuLinks.normalize("  map.kakao.com/?itemId=123 ")).isEqualTo("https://map.kakao.com/?itemId=123");
		assertThat(MenuLinks.normalize("HTTP://Maps.Google.com/x")).isEqualTo("HTTP://Maps.Google.com/x");
		assertThat(MenuLinks.normalize("   ")).isNull();
		assertThat(MenuLinks.normalize(null)).isNull();
	}

	@Test
	void http_https_주소가_아니면_거절한다() {
		assertThatThrownBy(() -> MenuLinks.normalize("javascript:alert(1)")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> MenuLinks.normalize("김밥천국")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> MenuLinks.normalize("ftp://example.com/a")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> MenuLinks.normalize("https://" + "a".repeat(500) + ".com"))
			.isInstanceOf(ApiException.class);
	}
}
