-- 쪽지(Notion 「21. 같은 조직에 추가된 사용자들끼리 쪽지 주고받기 기능」): 같은 조직 멤버끼리 한 통씩 주고받는다. 투표와 상관없다.
-- 익명 쪽지도 보낸 사람을 저장하지만 받은 사람의 응답에는 내보내지 않는다(신고된 쪽지만 관리자가 실제 보낸 사람을 본다).
-- 사용자는 행을 지우지 않고 쪽마다 소프트 삭제한다(내 쪽지함에서만 지운다). 그래서 신고가 쪽지를 잃지 않는다.
CREATE TABLE letters (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 어느 조직에서 보냈는지. 조직이 없어지면 NULL(「삭제된 조직」)
    organization_id      BIGINT REFERENCES organizations (id) ON DELETE SET NULL,
    -- 강제 탈퇴로 회원이 지워지면 NULL(「탈퇴한 사용자」)
    sender_id            BIGINT REFERENCES users (id) ON DELETE SET NULL,
    recipient_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reply_to_id          BIGINT REFERENCES letters (id) ON DELETE SET NULL,
    anonymous            BOOLEAN      NOT NULL DEFAULT false,
    -- 익명 쪽지에 쓴 답장: 답장한 사람(보낸 사람)에게 받는 사람을 숨긴다
    recipient_hidden     BOOLEAN      NOT NULL DEFAULT false,
    body                 VARCHAR(500) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at              TIMESTAMPTZ,
    sender_deleted_at    TIMESTAMPTZ,
    -- 받은 사람이 지웠거나, 받은 사람이 차단해 두어 받지 않은 쪽지
    recipient_deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_letters_not_self CHECK (sender_id IS NULL OR sender_id <> recipient_id)
);
CREATE INDEX idx_letters_received ON letters (recipient_id, id) WHERE recipient_deleted_at IS NULL;
CREATE INDEX idx_letters_unread ON letters (recipient_id) WHERE read_at IS NULL AND recipient_deleted_at IS NULL;
-- 보낸 쪽지함, 그리고 회원을 지울 때 sender_id를 비우는 FK 처리에 쓴다(조건 없는 인덱스).
CREATE INDEX idx_letters_sender ON letters (sender_id, id);
CREATE INDEX idx_letters_reply_to ON letters (reply_to_id);

-- 받은 사람이 보낸 사람을 차단한다. 익명 쪽지에서 한 차단과 실명 쪽지에서 한 차단을 나눈다(차단 때문에 익명이 드러나지 않게).
CREATE TABLE letter_blocks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 차단한 쪽지(익명 차단 목록에 그 쪽지의 첫 줄을 보여준다)
    letter_id       BIGINT REFERENCES letters (id) ON DELETE SET NULL,
    anonymous       BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_letter_blocks UNIQUE (user_id, blocked_user_id, anonymous)
);
CREATE INDEX idx_letter_blocks_blocked ON letter_blocks (blocked_user_id);
CREATE INDEX idx_letter_blocks_letter ON letter_blocks (letter_id);

-- 받은 사람이 신고한 쪽지. 관리자 콘솔 「신고」에서 쪽지와 실제 보낸 사람을 보고 처리한다. 한 쪽지는 한 번만 신고된다.
CREATE TABLE letter_reports (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    letter_id   BIGINT      NOT NULL UNIQUE REFERENCES letters (id) ON DELETE CASCADE,
    reason      VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_letter_reports_open ON letter_reports (id) WHERE resolved_at IS NULL;
