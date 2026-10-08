#!/usr/bin/env bash
# PostgreSQL과 프로필 사진의 일일 백업. 서버의 crontab에 등록한다(docs/DEPLOY.md 참고).
#   0 4 * * * $HOME/ohjumwhat/backup.sh >> $HOME/ohjumwhat/backup.log 2>&1
# 백업은 ~/ohjumwhat/backups/ 에 쌓이고, 14일이 지난 것은 지운다.
# DB(users.photo_key)가 사진 파일을 가리키므로 둘은 같은 시각의 것으로 함께 복원한다.
set -euo pipefail

cd "$(dirname "$0")"
# .env는 source하지 않는다. compose env_file 형식이라 셸 문법이 아닌 값(예: 쉼표 뒤 공백)이 있으면
# bash가 그 값을 명령으로 실행하다 멈춘다. DB 이름·계정은 db 컨테이너의 환경변수(compose가 .env에서 넣은 값)를 쓴다.

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M)"
file="backups/ohjumwhat-$stamp.sql.gz"
photos="backups/ohjumwhat-photos-$stamp.tar.gz"
# 실패하거나 중단되면(Ctrl-C 등) 쓰던 파일을 지운다. 반쯤 쓴 파일이 정상 백업처럼 남지 않게 한다.
partial=""
fail() { rm -f "$partial"; echo "$(date '+%F %T') 백업 실패" >&2; }
trap fail ERR
trap 'fail; exit 1' INT TERM HUP

# DB를 먼저 뜬다. 사진 백업이 실패해도 DB 백업은 남는다.
partial="$file"
docker compose exec -T db sh -c '
	: "${POSTGRES_USER:?db 컨테이너에 POSTGRES_USER가 없습니다(.env의 DB_USERNAME)}"
	: "${POSTGRES_DB:?db 컨테이너에 POSTGRES_DB가 없습니다(.env의 DB_NAME)}"
	exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner
' | gzip > "$file"
# 쓰는 중인 임시 파일(*.tmp)과 채팅 사진(photos/chat, 30일이면 지워지는 사진이라 매일 백업하면 용량만 커진다)은 뺀다. 백업 중에 사진을 올리거나 지우면 GNU tar가 경고와 함께 1로 끝나지만
# 아카이브는 온전하므로 성공으로 본다(컨테이너 안에서 바꿔서, 컨테이너가 없어 compose가 1로 끝나는 경우와 구분한다).
partial="$photos"
docker compose exec -T app sh -c '
	tar czf - --exclude="*.tmp" --exclude="photos/chat" -C /data photos
	rc=$?
	[ "$rc" -eq 1 ] && rc=0
	exit "$rc"
' > "$photos"
partial=""

find backups \( -name 'ohjumwhat-*.sql.gz' -o -name 'ohjumwhat-photos-*.tar.gz' \) -mtime +14 -delete \
	|| echo "$(date '+%F %T') 14일이 지난 백업을 지우지 못했습니다" >&2
echo "$(date '+%F %T') 백업 완료: $file ($(du -h "$file" | cut -f1)), $photos ($(du -h "$photos" | cut -f1))"
