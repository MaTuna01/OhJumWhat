package com.ohjumwhat.place;

import java.util.Optional;

/**
 * 카카오 로컬 API(서버에서만 부른다). 결과는 약관상 장소 ID·장소 링크 말고는 저장할 수 없어서, 부를 때마다 화면에 보여줄 값으로만 쓴다.
 * 테스트에서는 정해진 표로 답하는 가짜로 바꾼다.
 */
public interface KakaoLocal {

	/** 키가 설정되어 있어 쓸 수 있는지 */
	boolean enabled();

	/**
	 * 주소(또는 「역삼역」 같은 장소 이름)의 좌표. 못 찾으면 빈 값.
	 *
	 * @throws PlaceSearchUnavailableException 쓸 수 없거나 응답이 실패했을 때
	 */
	Optional<Coordinate> geocode(String query);

	/**
	 * center 근처 음식점(분류 FD6)을 가까운 순으로 15개씩. query가 비면 분류 검색(근처 둘러보기)이다.
	 *
	 * @param radius 반경(m, 20000까지)
	 * @param page 1부터. 카카오는 45개(3페이지)까지만 준다
	 * @throws PlaceSearchUnavailableException 쓸 수 없거나 응답이 실패했을 때
	 */
	KakaoPlace.Page searchRestaurants(String query, Coordinate center, int radius, int page);
}
