-- 방명록(이슈 #91): 나와 조직을 하나라도 같이 쓰는 사람의 프로필에 한 줄 글을 남긴다. 조직이 아니라 사람에게 속한다.
-- 사용자는 행을 지우지 않고 소프트 삭제한다(deleted_at). 그래서 신고가 글을 잃지 않고, 지워서 제재를 피하지 못한다.
CREATE TABLE guestbook_entries (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 방명록 주인. 강제 탈퇴로 회원이 지워지면 NULL(글과 신고는 남는다)
    owner_id      BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- 쓴 사람. 강제 탈퇴로 회원이 지워지면 NULL(「탈퇴한 사용자」)
    author_id     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    body          VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 쓴 사람이나 주인이 지웠다
    deleted_at    TIMESTAMPTZ,
    -- 관리자가 신고를 받아 글을 제한했다(본문을 누구에게도 내보내지 않고, 쓴 사람에게 경고한다)
    restricted_at TIMESTAMPTZ,
    CONSTRAINT ck_guestbook_entries_not_self CHECK (owner_id IS NULL OR author_id IS NULL OR owner_id <> author_id)
);
-- 방명록 목록(주인, 최신순)과 쓴 사람의 경고(제한 시각). 회원을 지울 때 FK 처리에도 쓰도록 조건 없이 만든다
-- (조건이 붙은 부분 인덱스는 FK 처리에 쓰지 못한다, V17 참고).
CREATE INDEX idx_guestbook_entries_owner ON guestbook_entries (owner_id, id);
CREATE INDEX idx_guestbook_entries_author ON guestbook_entries (author_id, restricted_at);

-- 주인이 신고한 방명록 글. 신고하는 사람은 언제나 주인이라 신고자 컬럼을 두지 않는다. 한 글은 한 번만 신고된다.
-- 관리자는 「글 제한」(RESTRICTED) 또는 「문제 없음」(DISMISSED)으로 처리한다.
CREATE TABLE guestbook_reports (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id    BIGINT      NOT NULL UNIQUE REFERENCES guestbook_entries (id) ON DELETE CASCADE,
    reason      VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolution  VARCHAR(20)
        CONSTRAINT ck_guestbook_reports_resolution CHECK (resolution IN ('RESTRICTED', 'DISMISSED')),
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- 처리 결과와 처리 시각은 함께 비거나 함께 찬다
    CONSTRAINT ck_guestbook_reports_resolved CHECK ((resolution IS NULL) = (resolved_at IS NULL))
);
CREATE INDEX idx_guestbook_reports_open ON guestbook_reports (id) WHERE resolved_at IS NULL;
-- 관리자 회원을 지울 때 resolved_by를 비우는 FK 처리에 쓴다.
CREATE INDEX idx_guestbook_reports_resolved_by ON guestbook_reports (resolved_by);

-- 내 방명록을 마지막으로 본 시각(이보다 늦게 쓰인 글이 새 글)과, 제한된 내 글의 경고를 마지막으로 확인한 시각.
-- NULL이면 모두 새것이다. users.created_at은 Clock 값이 아니라서 기준으로 쓰지 않는다.
ALTER TABLE users
    ADD COLUMN guestbook_seen_at          TIMESTAMPTZ,
    ADD COLUMN guestbook_warnings_seen_at TIMESTAMPTZ;
