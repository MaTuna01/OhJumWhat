-- 직접 올린 프로필 사진. 사진 파일은 서버 디스크(ohjumwhat.photos.dir)에 {photo_key}.jpg로 두고, DB에는 키만 둔다.
-- 있으면 화면에 구글 사진(profile_image_url) 대신 보여준다. 구글 사진은 로그인 때마다 갱신되고 올린 사진은 그대로 둔다.
-- 새로 올릴 때마다 키가 바뀌어 사진 주소가 달라지므로, 브라우저가 사진을 오래 캐시해도 된다.
ALTER TABLE users ADD COLUMN photo_key VARCHAR(32);
