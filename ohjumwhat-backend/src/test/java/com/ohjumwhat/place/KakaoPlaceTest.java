package com.ohjumwhat.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class KakaoPlaceTest {

	@Test
	void 분류는_가장_자세한_것을_보여주고_체인_이름이면_그_앞의_분류를_쓴다() {
		assertThat(place("BHC치킨 가산디지털점", "치킨", "BHC치킨").category()).isEqualTo("치킨");
		assertThat(place("두끼떡볶이 가산점", "분식", "떡볶이", "두끼떡볶이").category()).isEqualTo("떡볶이");
		assertThat(place("헬키 푸키 가산점", "분식", "헬키푸키").category()).isEqualTo("분식");
		assertThat(place("할매집", "한식", "찌개,전골").category()).isEqualTo("찌개,전골");
		assertThat(place("김밥천국", "분식").category()).isEqualTo("분식");
		assertThat(place("삼형제김밥").category()).isNull();
	}

	@Test
	void 검색어가_이름이나_분류에_있는지_띄어쓰기와_대소문자를_무시하고_본다() {
		assertThat(place("칠공주떡볶이", "분식", "떡볶이").matches("떡볶이")).isTrue();
		assertThat(place("왕김말이랑 떡볶이", "분식").matches("떡 볶이")).isTrue();
		assertThat(place("먹쉬돈나", "분식", "떡볶이").matches("떡볶이")).isTrue();
		assertThat(place("bhc치킨 가산점", "치킨", "BHC치킨").matches("BHC")).isTrue();
		assertThat(place("BHC치킨 가산디지털점", "치킨", "BHC치킨").matches("떡볶이")).isFalse();
	}

	private static KakaoPlace place(String name, String... categories) {
		return new KakaoPlace("1", name, List.of(categories), null, new Coordinate(37.5, 127.0), null);
	}
}
