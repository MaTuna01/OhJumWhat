-- 근처 식당 찾기(카카오 로컬)로 고른 식당. 약관상 카카오 결과는 장소 ID·장소 링크만 저장할 수 있어서,
-- 이름·주소·좌표는 저장하지 않고 볼 때마다 같은 검색어로 다시 찾아 ID가 같은 결과를 쓴다.
ALTER TABLE menu_options
    ADD COLUMN kakao_place_id VARCHAR(20),  -- 카카오 장소 ID(링크는 link_url = https://place.map.kakao.com/{id})
    ADD COLUMN place_query    VARCHAR(100); -- 식당을 찾을 때 사용자가 친 검색어(비면 근처 둘러보기)
