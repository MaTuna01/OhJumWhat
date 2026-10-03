-- 투표 채팅. 투표가 열린 때부터 마감 1시간 뒤까지 쓰고, 그 뒤에는 읽기만 한다. 투표를 지우면 함께 지운다.
-- 지운 메시지는 대화 흐름이 끊기지 않게 행을 남기고 본문만 비운다("삭제된 메시지예요").
-- 강제 탈퇴로 회원이 지워지면 user_id만 NULL이 된다("탈퇴한 사용자").
CREATE TABLE chat_messages (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    BIGINT       NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    user_id    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    body       VARCHAR(300),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    edited_at  TIMESTAMPTZ, -- 마지막으로 고친 시각(고친 적이 없으면 NULL)
    deleted_at TIMESTAMPTZ, -- 지운 시각(지우면 body는 NULL)
    CONSTRAINT ck_chat_messages_body CHECK ((deleted_at IS NULL) = (body IS NOT NULL))
);
CREATE INDEX idx_chat_messages_poll ON chat_messages (poll_id, id);
