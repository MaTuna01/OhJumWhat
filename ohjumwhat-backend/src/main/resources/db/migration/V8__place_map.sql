-- 식당 지도(지도 연동 3단계): 공유 글의 식당 주소, 회사 주소·검색 반경.
-- 모두 사용자가 붙여 넣거나 직접 입력한 값이다. 좌표는 약관상 저장할 수 없어서 볼 때마다 주소로 변환한다.
ALTER TABLE menu_options ADD COLUMN place_address VARCHAR(200); -- 식당 주소(공유 글의 주소 줄). 링크(link_url)가 있을 때만

ALTER TABLE organizations
    ADD COLUMN office_address VARCHAR(200),                       -- 회사 주소. 근처 식당 검색·지도의 기준점
    ADD COLUMN search_radius  INTEGER NOT NULL DEFAULT 1000       -- 근처 식당 검색 반경(m)
        CONSTRAINT organizations_search_radius_check CHECK (search_radius IN (500, 1000, 2000));
