package com.ohjumwhat.place;

import java.util.Optional;

/** naver.me 단축 링크가 가리키는 주소(첫 리디렉션의 Location). 테스트에서는 가짜로 바꾼다. */
public interface NaverShortLinks {

	/** 확인하지 못하면(오류·시간 초과·리디렉션이 아님) 빈 값 */
	Optional<String> location(String code);
}
