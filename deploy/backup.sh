#!/usr/bin/env bash
# PostgreSQL과 프로필 사진의 일일 백업. 서버의 crontab에 등록한다(docs/DEPLOY.md 참고).
#   0 4 * * * $HOME/ohjumwhat/backup.sh >> $HOME/ohjumwhat/backup.log 2>&1
# 백업은 ~/ohjumwhat/backups/ 에 쌓이고, 14일이 지난 것은 지운다.
# DB(users.photo_key)가 사진 파일을 가리키므로 둘은 같은 시각의 것으로 함께 복원한다.
set -euo pipefail

cd "$(dirname "$0")"
set -a; source .env; set +a

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M)"
file="backups/ohjumwhat-$stamp.sql.gz"
photos="backups/ohjumwhat-photos-$stamp.tar.gz"
# DB를 먼저 뜬다. 사진 백업이 실패해도 DB 백업은 남는다.
docker compose exec -T db pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --no-owner | gzip > "$file"
# 쓰는 중인 임시 파일(*.tmp)은 뺀다.
docker compose exec -T app tar czf - --exclude='*.tmp' -C /data photos > "$photos"
find backups \( -name 'ohjumwhat-*.sql.gz' -o -name 'ohjumwhat-photos-*.tar.gz' \) -mtime +14 -delete
echo "$(date '+%F %T') 백업 완료: $file ($(du -h "$file" | cut -f1)), $photos ($(du -h "$photos" | cut -f1))"
