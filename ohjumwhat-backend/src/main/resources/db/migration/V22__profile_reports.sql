-- 사람 신고(이슈 #115): 같은 조직 멤버의 프로필 모달에서 사람을 신고한다(프로필 내용 + 행동). 관리자가 「문제 없음」으로
-- 처리하거나, 그 사람에게 제재를 걸거나(경고 포함), 강제 탈퇴시키면 처리된다. 신고한 사람에게는 결과를 알린다.
-- 행은 지우지 않는다(기록). 신고한 사람·신고된 사람이 강제 탈퇴해도 신고는 남는다.
CREATE TABLE profile_reports (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 신고한 사람. 강제 탈퇴로 회원이 지워지면 NULL(신고와 결과는 남는다)
    reporter_id         BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- 신고된 사람. 강제 탈퇴로 회원이 지워지면 NULL(신고할 때의 프로필 사본은 남는다)
    target_id           BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- 사유 분류(SanctionReason과 같다)와 설명(선택, 한 줄)
    reason              VARCHAR(20)   NOT NULL,
    detail              VARCHAR(100),
    -- 신고할 때 신고된 사람의 프로필 사본: 이름(별명, 없으면 구글 이름)·한줄 소개·좋아하는 음식·취미·직급. 사진은 두지 않는다.
    target_name         VARCHAR(100)  NOT NULL,
    target_bio          VARCHAR(50),
    target_food_tags    VARCHAR(10)[] NOT NULL DEFAULT '{}',
    target_hobbies      VARCHAR(10)[] NOT NULL DEFAULT '{}',
    target_job_title    VARCHAR(15),
    -- Clock 값이다(DB 기본값을 쓰지 않는다)
    created_at          TIMESTAMPTZ   NOT NULL,
    -- 처리 결과(DISMISSED 문제 없음, ACTIONED 제재, WITHDRAWN 강제 탈퇴)와 처리 시각·관리자
    resolution          VARCHAR(20),
    resolved_at         TIMESTAMPTZ,
    resolved_by         BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- 함께 처리한 제재(ACTIONED). 제재는 회원과 함께 지워지므로 그때는 NULL이고, 결과는 아래 사본으로 남는다
    sanction_id         BIGINT REFERENCES user_sanctions (id) ON DELETE SET NULL,
    -- 신고한 사람에게 보여줄 결과 사본(ACTIONED): 제재에 실제로 저장된 제한·초기화·끝나는 시각. 나중에 해제·탈퇴돼도 바뀌지 않는다
    result_restrictions VARCHAR(20)[] NOT NULL DEFAULT '{}',
    result_resets       VARCHAR(20)[] NOT NULL DEFAULT '{}',
    result_ends_at      TIMESTAMPTZ,
    -- 신고한 사람이 결과 창을 본 시각(NULL이면 아직 보지 않았다)
    result_seen_at      TIMESTAMPTZ,
    CONSTRAINT ck_profile_reports_reason CHECK (reason IN ('PROFILE', 'ABUSE', 'SPAM', 'ETC')),
    CONSTRAINT ck_profile_reports_resolution CHECK (resolution IN ('DISMISSED', 'ACTIONED', 'WITHDRAWN')),
    -- 처리 결과와 처리 시각은 함께 비거나 함께 찬다
    CONSTRAINT ck_profile_reports_resolved CHECK ((resolution IS NULL) = (resolved_at IS NULL)),
    CONSTRAINT ck_profile_reports_not_self CHECK (reporter_id IS NULL OR target_id IS NULL OR reporter_id <> target_id)
);
-- 같은 (신고한 사람, 신고된 사람)의 처리 전 신고는 하나다(다시 보내도 그대로, 처리된 뒤에는 다시 신고할 수 있다).
CREATE UNIQUE INDEX uk_profile_reports_open ON profile_reports (reporter_id, target_id) WHERE resolved_at IS NULL;
-- 신고된 사람의 처리 전 신고(제재·강제 탈퇴가 함께 처리), 신고한 사람의 결과 안내. 회원을 지울 때 FK 처리에도 쓰도록 조건 없이
-- 만든다(조건이 붙은 부분 인덱스는 FK 처리에 쓰지 못한다, V17 참고).
CREATE INDEX idx_profile_reports_target ON profile_reports (target_id, id);
CREATE INDEX idx_profile_reports_reporter ON profile_reports (reporter_id, id);
-- 관리자 회원을 지울 때 resolved_by를, 제재가 지워질 때 sanction_id를 비우는 FK 처리에 쓴다.
CREATE INDEX idx_profile_reports_resolved_by ON profile_reports (resolved_by);
CREATE INDEX idx_profile_reports_sanction ON profile_reports (sanction_id);
-- 관리자 콘솔의 처리 전 목록과 개수
CREATE INDEX idx_profile_reports_open ON profile_reports (id) WHERE resolved_at IS NULL;
