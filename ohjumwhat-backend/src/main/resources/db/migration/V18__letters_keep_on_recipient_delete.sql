-- 받은 사람이 강제 탈퇴해도 쪽지를 지우지 않고 recipient_id만 비운다(#77).
-- 지우면 익명 상대에게 쓴 답장이 보낸 쪽지함에서 갑자기 사라지고 그 뒤 답장의 원문 표시도 없어져, 그 익명 상대가 방금 탈퇴했다는
-- 것이 드러났다. 그 사람이 신고한 쪽지와 신고도 함께 지워져 관리자가 증거를 잃었다.
ALTER TABLE letters ALTER COLUMN recipient_id DROP NOT NULL;
ALTER TABLE letters DROP CONSTRAINT letters_recipient_id_fkey;
ALTER TABLE letters ADD CONSTRAINT letters_recipient_id_fkey
    FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE SET NULL;
