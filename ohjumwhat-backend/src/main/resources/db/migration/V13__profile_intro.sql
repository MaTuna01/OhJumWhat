-- 프로필의 한줄 소개와 좋아하는 음식(직접 적는 태그, 최대 3개). 같은 조직 멤버가 프로필을 열면 보인다.
-- 로그인은 회원 행 전체를 다시 쓰므로, 두 컬럼은 엔티티 저장으로 쓰지 않고 UserRepository.updateIntro로만 바꾼다.
ALTER TABLE users ADD COLUMN bio VARCHAR(50);
ALTER TABLE users ADD COLUMN food_tags VARCHAR(10)[] NOT NULL DEFAULT '{}'
    CONSTRAINT ck_users_food_tags CHECK (cardinality(food_tags) <= 3);
