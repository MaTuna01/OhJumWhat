-- 상세 프로필(Notion 「22. 프로필 항목 추가」): MBTI·퍼스널컬러·취미·나이·직급. 같은 조직 멤버가 프로필을 열면 보인다.
-- 「프로필 수정」에서 다섯 항목을 모두 채워야 저장되고 지우는 기능은 없어서, 모두 비었거나(채우기 전) 모두 채워진 상태만 있다.
-- 소개(V13)처럼 로그인이 회원 행을 다시 쓸 때 되쓰이지 않게 엔티티 저장으로 쓰지 않고 UserRepository.updateDetails로만 바꾼다.
ALTER TABLE users
    ADD COLUMN mbti           VARCHAR(4)
        CONSTRAINT ck_users_mbti CHECK (mbti ~ '^[EI][SN][TF][JP]$'),
    ADD COLUMN personal_color VARCHAR(12)
        CONSTRAINT ck_users_personal_color
            CHECK (personal_color IN ('SPRING_WARM', 'SUMMER_COOL', 'AUTUMN_WARM', 'WINTER_COOL')),
    ADD COLUMN hobbies        VARCHAR(10)[] NOT NULL DEFAULT '{}'
        CONSTRAINT ck_users_hobbies CHECK (cardinality(hobbies) <= 5),
    ADD COLUMN age            SMALLINT
        CONSTRAINT ck_users_age CHECK (age BETWEEN 1 AND 120),
    ADD COLUMN job_title      VARCHAR(15);

ALTER TABLE users
    ADD CONSTRAINT ck_users_profile_details CHECK (
        (mbti IS NULL AND personal_color IS NULL AND age IS NULL AND job_title IS NULL AND cardinality(hobbies) = 0)
        OR (mbti IS NOT NULL AND personal_color IS NOT NULL AND age IS NOT NULL AND job_title IS NOT NULL
            AND cardinality(hobbies) >= 1));
