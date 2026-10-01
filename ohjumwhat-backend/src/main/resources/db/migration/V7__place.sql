-- 식당 정보(네이버 지도 연동): 메뉴에 붙인 식당 이름, 조직 위치(검색 지역·회사 위치).
-- 지도 검색 API 결과는 약관상 저장할 수 없어서, 사용자가 붙여 넣은 링크와 이름만 저장한다.
-- 네이버 장소 ID는 정식 링크(https://map.naver.com/p/entry/place/{id})에 들어 있어 따로 두지 않는다.
ALTER TABLE menu_options ADD COLUMN place_name VARCHAR(100); -- 식당 이름. 링크(link_url)가 있을 때만

ALTER TABLE organizations
    ADD COLUMN area            VARCHAR(20),  -- 검색 지역(예: 역삼동). 「네이버 지도에서 찾기」 검색어 앞에 붙인다
    ADD COLUMN office_name     VARCHAR(100), -- 회사 위치 이름
    ADD COLUMN office_link_url VARCHAR(500); -- 회사 위치 지도 링크
