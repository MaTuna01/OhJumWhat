-- 웹 푸시(FCM)를 받을 기기(이슈 #102). 브라우저의 Firebase 설치 ID(FID)를 그 기기에서 켠 로그인(세션)에 묶는다.
-- 로그아웃·세션 만료(30일)·강제 탈퇴의 세션 삭제·회원 삭제 때 함께 지워진다(FK CASCADE). Spring Session은 로그인할 때
-- session_id만 바꾸고 primary_id는 그대로 두므로 primary_id에 묶는다.
-- 같은 브라우저에서 다른 계정이 알림을 켜면 그 기기는 그 사람에게 옮겨 간다(fid UNIQUE). 한 사람당 최근 10대까지만 남긴다.
CREATE TABLE push_devices (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fid                VARCHAR(64) NOT NULL UNIQUE,
    session_primary_id CHAR(36)    NOT NULL REFERENCES spring_session (primary_id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 마지막으로 등록(다시 켜기·앱을 열 때 조용히 다시 등록)한 시각. 10대를 넘으면 오래된 것부터 지운다.
    last_seen_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_push_devices_user ON push_devices (user_id);
CREATE INDEX idx_push_devices_session ON push_devices (session_primary_id);
