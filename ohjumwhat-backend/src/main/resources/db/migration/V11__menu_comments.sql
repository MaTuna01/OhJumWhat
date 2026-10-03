-- 메뉴 댓글. 투표가 진행 중일 때만 쓰고 고치고 지운다(마감된 투표는 기록이라 읽기만 한다).
-- 메뉴를 지우면 댓글도 함께 지운다. 강제 탈퇴로 회원이 지워지면 user_id만 NULL이 된다("탈퇴한 사용자").
CREATE TABLE menu_comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    option_id  BIGINT       NOT NULL REFERENCES menu_options (id) ON DELETE CASCADE,
    user_id    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    body       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    edited_at  TIMESTAMPTZ  -- 마지막으로 고친 시각(고친 적이 없으면 NULL)
);
CREATE INDEX idx_menu_comments_option ON menu_comments (option_id, id);
