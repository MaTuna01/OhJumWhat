-- 새 소식: 업데이트(릴리스 노트, 저장소 파일 release-notes/{버전}.md에서 서버 시작 때 자동 게시)와
-- 개발자 노트(관리자 콘솔에서 작성). 고쳐도 published_at은 그대로라 다시 알리지 않는다.
CREATE TABLE notices (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind         VARCHAR(20)   NOT NULL
        CONSTRAINT ck_notices_kind CHECK (kind IN ('RELEASE', 'NOTE')),
    version      VARCHAR(20)   UNIQUE,                                  -- 업데이트 글만. 예: 1.5.0
    title        VARCHAR(100)  NOT NULL,
    body         VARCHAR(5000) NOT NULL,
    created_by   BIGINT        REFERENCES users (id) ON DELETE SET NULL, -- 개발자 노트를 쓴 관리자
    published_at TIMESTAMPTZ   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_notices_version CHECK ((kind = 'RELEASE') = (version IS NOT NULL))
);
CREATE INDEX idx_notices_published_at ON notices (published_at DESC);

-- 「새 소식」을 마지막으로 본 시각. 이보다 늦게 게시된 공지가 안 읽은 공지다. NULL이면 가입 시각으로 본다.
ALTER TABLE users ADD COLUMN notices_seen_at TIMESTAMPTZ;
