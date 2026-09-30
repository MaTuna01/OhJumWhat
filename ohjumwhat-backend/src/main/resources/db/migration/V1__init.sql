-- 오점왓 초기 스키마

CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    google_sub        VARCHAR(255) NOT NULL UNIQUE,
    email             VARCHAR(320) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    profile_image_url VARCHAR(1024),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE organizations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL,
    invite_token VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_visited_at TIMESTAMPTZ,
    CONSTRAINT uk_memberships_org_user UNIQUE (organization_id, user_id)
);
CREATE INDEX idx_memberships_user ON memberships (user_id);

CREATE TABLE poll_schedules (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(50) NOT NULL,
    days_of_week    SMALLINT    NOT NULL CHECK (days_of_week BETWEEN 1 AND 127), -- 월=1 화=2 수=4 목=8 금=16 토=32 일=64
    open_time       TIME        NOT NULL, -- 한국 시간
    close_time      TIME        NOT NULL, -- 한국 시간
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_poll_schedules_time CHECK (close_time > open_time)
);
CREATE INDEX idx_poll_schedules_org ON poll_schedules (organization_id);

CREATE TABLE polls (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    schedule_id     BIGINT REFERENCES poll_schedules (id) ON DELETE SET NULL, -- NULL이면 수동 생성
    created_by      BIGINT REFERENCES users (id) ON DELETE SET NULL,          -- NULL이면 정기 투표
    title           VARCHAR(100) NOT NULL,
    poll_date       DATE         NOT NULL, -- 한국 날짜
    opens_at        TIMESTAMPTZ  NOT NULL,
    closes_at       TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_polls_schedule_date UNIQUE (schedule_id, poll_date),
    CONSTRAINT ck_polls_time CHECK (closes_at > opens_at)
);
CREATE INDEX idx_polls_org_date ON polls (organization_id, poll_date);

CREATE TABLE menu_options (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    BIGINT      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    created_by BIGINT      NOT NULL REFERENCES users (id),
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_menu_options_poll_name UNIQUE (poll_id, name)
);

CREATE TABLE votes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    BIGINT      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- NULL이면 "오늘은 패스". 참여자가 있는 메뉴는 삭제할 수 없다.
    -- RESTRICT 대신 NO ACTION(기본값)을 써서 투표가 CASCADE로 지워질 때는 문장 끝에 검사되도록 한다.
    option_id  BIGINT REFERENCES menu_options (id) ON DELETE NO ACTION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_votes_poll_user UNIQUE (poll_id, user_id)
);
CREATE INDEX idx_votes_option ON votes (option_id);
