#!/usr/bin/env bash
# PostgreSQL 일일 백업. 서버의 crontab에 등록한다(docs/DEPLOY.md 참고).
#   0 4 * * * $HOME/ohjumwhat/backup.sh >> $HOME/ohjumwhat/backup.log 2>&1
# 백업은 ~/ohjumwhat/backups/ 에 쌓이고, 14일이 지난 것은 지운다.
set -euo pipefail

cd "$(dirname "$0")"
set -a; source .env; set +a

mkdir -p backups
file="backups/ohjumwhat-$(date +%Y%m%d-%H%M).sql.gz"
docker compose exec -T db pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --no-owner | gzip > "$file"
find backups -name 'ohjumwhat-*.sql.gz' -mtime +14 -delete
echo "$(date '+%F %T') 백업 완료: $file ($(du -h "$file" | cut -f1))"
