package com.ohjumwhat.place;

import java.util.Optional;

/** naver.me 단축 링크가 가리키는 주소(첫 리디렉션의 Location). 테스트에서는 가짜로 바꾼다. */
public interface NaverShortLinks {

	/**
	 * 확인하지 못하면(오류·시간 초과·리디렉션이 아님) 빈 값.
	 *
	 * @throws NotFound naver.me가 없는 코드라고 답했을 때(404·410). 잘못 붙인 링크라 저장하지 않는다
	 */
	Optional<String> location(String code);

	/** naver.me에 없는 단축 링크(404·410) */
	class NotFound extends RuntimeException {

		public NotFound() {
			super("naver.me 단축 링크가 없어요.");
		}
	}
}
