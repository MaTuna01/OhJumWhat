package com.ohjumwhat.place;

/**
 * 위도·경도(WGS84). 지도 서비스에서 받은 좌표는 약관상 저장하지 않고 화면에 보여줄 때만 쓴다.
 *
 * @param lat 위도
 * @param lng 경도
 */
public record Coordinate(double lat, double lng) {
}
