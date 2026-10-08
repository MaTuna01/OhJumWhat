-- 채팅 사진(이슈 #135). 사진 메시지는 글 없이 사진 1장이고, 파일은 {key}.jpg(원본)·{key}_t.jpg(썸네일)로 디스크에 둔다.
-- 사진은 30일 동안 보이고 그 뒤에는 「보관 기간이 지난 사진이에요」다. 만료는 마감처럼 요청 시각(created_at + 30일)으로 판정하고
-- 이 컬럼은 그대로 둔다(파일은 매일 정리 작업이 지운다). 지우면 글처럼 사진 키와 크기도 비운다.
ALTER TABLE chat_messages
    ADD COLUMN image_key    VARCHAR(32), -- 사진 파일 키(UUID hex 32자). 글 메시지는 NULL
    ADD COLUMN image_width  INTEGER,     -- 서버가 만든 원본의 크기(px). 화면이 사진이 뜨기 전에 자리를 잡는다
    ADD COLUMN image_height INTEGER;

-- 지운 메시지는 글도 사진도 없고, 지우지 않은 메시지는 글과 사진 중 하나만 있다.
ALTER TABLE chat_messages DROP CONSTRAINT ck_chat_messages_body;
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_body CHECK (
    (deleted_at IS NOT NULL AND body IS NULL AND image_key IS NULL)
    OR (deleted_at IS NULL AND (body IS NULL) <> (image_key IS NULL)));
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_image CHECK (
    (image_key IS NULL) = (image_width IS NULL) AND (image_key IS NULL) = (image_height IS NULL)
    AND (image_width IS NULL OR (image_width > 0 AND image_height > 0)));

-- 사진 주소(키)로 메시지를 찾는다(보기 권한 확인, 정리 작업).
CREATE UNIQUE INDEX uk_chat_messages_image_key ON chat_messages (image_key) WHERE image_key IS NOT NULL;
