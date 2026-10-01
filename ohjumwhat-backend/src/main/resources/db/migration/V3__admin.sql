-- 관리자 콘솔: 회원 역할과 최근 로그인, 강제 탈퇴(같은 구글 계정 재가입 차단)

-- 관리자는 서버 설정(ADMIN_EMAILS)으로 지정한다. 서버 시작·로그인 때 이 값을 맞춘다.
ALTER TABLE users
    ADD COLUMN role          VARCHAR(20) NOT NULL DEFAULT 'USER'
        CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    ADD COLUMN last_login_at TIMESTAMPTZ;

-- 강제 탈퇴로 회원을 지워도 그 사람이 올린 메뉴(다른 사람들이 참여 중일 수 있다)는 남기고 작성자만 비운다.
ALTER TABLE menu_options ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE menu_options DROP CONSTRAINT menu_options_created_by_fkey;
ALTER TABLE menu_options
    ADD CONSTRAINT menu_options_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;

-- 관리자가 강제 탈퇴시킨 구글 계정. 같은 계정으로 다시 로그인하면 거절한다. 행을 지우면 차단이 풀린다.
CREATE TABLE blocked_accounts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    google_sub VARCHAR(255) NOT NULL UNIQUE,
    email      VARCHAR(320) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    blocked_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    blocked_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
