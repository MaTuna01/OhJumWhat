-- 별명. 있으면 화면에 구글 이름 대신 보여준다. 구글 이름(name)은 로그인 때마다 갱신되고 별명은 그대로 둔다.
ALTER TABLE users ADD COLUMN nickname VARCHAR(20);
