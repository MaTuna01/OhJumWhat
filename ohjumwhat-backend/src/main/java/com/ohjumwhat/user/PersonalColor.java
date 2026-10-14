package com.ohjumwhat.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** 퍼스널컬러(상세 프로필). 화면 라벨은 봄 웜·여름 쿨·가을 웜·겨울 쿨(lib/profileDetails.ts)이다. */
@Schema(description = "퍼스널컬러: SPRING_WARM 봄 웜, SUMMER_COOL 여름 쿨, AUTUMN_WARM 가을 웜, WINTER_COOL 겨울 쿨")
public enum PersonalColor {
	SPRING_WARM, SUMMER_COOL, AUTUMN_WARM, WINTER_COOL
}
