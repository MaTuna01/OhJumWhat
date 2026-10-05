-- 투표 채팅의 읽은 위치. (투표, 사람)마다 「이 메시지까지 봤다」를 한 행으로 둔다. 행이 없으면 0 = 아무것도 읽지 않음.
-- 안 읽은 메시지 = 그 투표의 메시지 중 남이 쓴 것(탈퇴한 사용자의 글 포함), 지우지 않은 것, ID가 읽은 위치보다 큰 것.
-- 위치는 뒤로 가지 않고 그 투표의 마지막 메시지를 넘지 않는다(ChatReadRepository.markRead).
-- 투표나 회원을 지우면 함께 지운다. 조직을 떠나도 남는다(다시 들어오면 이어서 본다).
CREATE TABLE chat_reads (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id              BIGINT      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    user_id              BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    last_read_message_id BIGINT      NOT NULL, -- 마지막으로 본 chat_messages.id
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_chat_reads_poll_user UNIQUE (poll_id, user_id)
);
