-- 이용 제한(제재, 이슈 #114): 관리자가 회원의 기능을 기간을 정해 막거나(제한), 프로필 항목을 비우거나(초기화), 경고만 보낸다.
-- 한 번의 처리가 한 행이다. 제한·초기화가 모두 비면 경고다. 행은 지우지 않고(기록), 해제하면 lifted_at만 채운다.
-- 기능이 막혔는지는 요청 시각으로 판정한다(활성 = 제한이 있고, 해제되지 않았고, 끝나는 시각 전). 별도 배치가 없다.
CREATE TABLE user_sanctions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 제재 받은 회원. 강제 탈퇴로 회원이 지워지면 기록도 함께 지운다
    user_id      BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 막은 기능(SUSPEND는 혼자만), 비운 프로필 항목
    restrictions VARCHAR(20)[] NOT NULL DEFAULT '{}',
    resets       VARCHAR(20)[] NOT NULL DEFAULT '{}',
    reason       VARCHAR(20)   NOT NULL,
    -- 관리자 설명(본인에게 보인다)
    note         VARCHAR(200),
    -- Clock 값이다(DB 기본값을 쓰지 않는다: 테스트의 고정 시계와 끝나는 시각을 맞춘다)
    created_at   TIMESTAMPTZ   NOT NULL,
    -- 제한이 끝나는 시각. NULL이면 해제할 때까지(제한이 있을 때)
    ends_at      TIMESTAMPTZ,
    -- 건 관리자. 관리자 회원이 지워지면 NULL(이름 없이 남는다)
    created_by   BIGINT REFERENCES users (id) ON DELETE SET NULL,
    lifted_at    TIMESTAMPTZ,
    lifted_by    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- 본인이 안내 창을 본 시각(NULL이면 아직 보지 않았다)
    seen_at      TIMESTAMPTZ,
    CONSTRAINT ck_user_sanctions_restrictions
        CHECK (restrictions <@ ARRAY ['SUSPEND','POLL','CHAT','LETTER','GUESTBOOK','PROFILE']::varchar[]),
    CONSTRAINT ck_user_sanctions_resets CHECK (resets <@ ARRAY ['NICKNAME','PHOTO','INTRO','DETAILS']::varchar[]),
    CONSTRAINT ck_user_sanctions_reason CHECK (reason IN ('PROFILE', 'ABUSE', 'SPAM', 'ETC')),
    -- 활동 정지는 다른 제한을 모두 포함하므로 혼자만 둔다
    CONSTRAINT ck_user_sanctions_suspend_alone
        CHECK (NOT ('SUSPEND' = ANY (restrictions)) OR cardinality(restrictions) = 1),
    -- 기간과 해제는 제한이 있을 때만 있다
    CONSTRAINT ck_user_sanctions_ends CHECK (ends_at IS NULL OR (cardinality(restrictions) > 0 AND ends_at > created_at)),
    CONSTRAINT ck_user_sanctions_lifted CHECK (lifted_at IS NULL OR cardinality(restrictions) > 0)
);
-- 회원의 제재 목록(요청마다 확인하는 활성 제재, 회원 상세)과 회원을 지울 때의 FK 처리
CREATE INDEX idx_user_sanctions_user ON user_sanctions (user_id, id);
-- 관리자 회원을 지울 때 created_by·lifted_by를 비우는 FK 처리에 쓴다.
CREATE INDEX idx_user_sanctions_created_by ON user_sanctions (created_by);
CREATE INDEX idx_user_sanctions_lifted_by ON user_sanctions (lifted_by);
