-- 익명 차단을 쪽지 한 통 단위로 바꾼다(#77). 보낸 사람 단위로 두면 익명 쪽지 하나를 차단했을 때 같은 사람의 다른 익명 쪽지까지
-- 사라지고, 차단 목록의 줄 수도 늘지 않아서 「이 익명 쪽지들은 같은 사람이 썼다」가 드러났다.
-- 실명 차단은 그대로 사람 단위다. 새로 오는 익명 쪽지는 그 사람에게 걸린 차단이 하나라도 있으면 받지 않는다(받은 사람은 알 수 없다).
ALTER TABLE letter_blocks DROP CONSTRAINT uk_letter_blocks;
CREATE UNIQUE INDEX uk_letter_blocks_named ON letter_blocks (user_id, blocked_user_id) WHERE NOT anonymous;
CREATE UNIQUE INDEX uk_letter_blocks_anonymous ON letter_blocks (user_id, letter_id) WHERE anonymous;
-- 보낸 사람이 강제 탈퇴해도 차단은 남긴다(blocked_user_id만 비운다). 지워지면 차단해 둔 익명 쪽지가 다시 보여서 그 쪽지를 보낸
-- 사람이 방금 탈퇴했다는 것이 드러난다. 탈퇴한 사람의 익명 쪽지도 그 쪽지 단위로 차단할 수 있다.
ALTER TABLE letter_blocks ALTER COLUMN blocked_user_id DROP NOT NULL;
ALTER TABLE letter_blocks DROP CONSTRAINT letter_blocks_blocked_user_id_fkey;
ALTER TABLE letter_blocks ADD CONSTRAINT letter_blocks_blocked_user_id_fkey
    FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE SET NULL;
-- 보낼 때 차단 확인(받는 사람, 보낸 사람)
CREATE INDEX idx_letter_blocks_user ON letter_blocks (user_id, blocked_user_id);

-- 회원을 지울 때(recipient_id CASCADE)와 조직을 지울 때(organization_id SET NULL) FK 처리가 letters 전체를 훑지 않게.
-- 받은 쪽지함 인덱스는 recipient_deleted_at 조건이 붙어 있어 FK 처리에 쓰지 못한다.
CREATE INDEX idx_letters_recipient ON letters (recipient_id);
CREATE INDEX idx_letters_organization ON letters (organization_id);
