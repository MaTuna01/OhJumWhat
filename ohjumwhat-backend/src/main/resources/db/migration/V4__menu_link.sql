-- 메뉴에 식당 지도 링크(선택). http/https 주소인지는 애플리케이션(MenuLinks)에서 검사한다.
ALTER TABLE menu_options ADD COLUMN link_url VARCHAR(500);
