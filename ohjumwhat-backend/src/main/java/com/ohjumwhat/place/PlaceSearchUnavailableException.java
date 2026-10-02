package com.ohjumwhat.place;

/** 지도 서비스(카카오 로컬)를 쓸 수 없을 때: 키가 없거나, 응답이 실패했거나, 시간이 넘었다. */
public class PlaceSearchUnavailableException extends RuntimeException {

	public PlaceSearchUnavailableException(String message) {
		super(message);
	}
}
