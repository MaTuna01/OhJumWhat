#!/usr/bin/env bash
# 측정 중 지표를 모은다. k6와 함께 백그라운드로 띄우고(RUN=이름), k6가 끝나면 Ctrl-C 또는 kill로 멈춘다.
#   RUN=steady-300 loadtest/collect.sh &      …k6 실행…      kill %1
# 10초마다 actuator 스냅샷(out/<RUN>/prom-<epoch>.txt)과 docker stats(out/<RUN>/docker-stats.jsonl)를 남기고, 끝날 때
# pg_stat_statements 상위 25개(pg-top.txt)·느린 쿼리 로그(slow.log)·pg_stat_activity 샘플(activity.log)을 저장한다.
# 운영 서버에서는 PROM_URL만 있고 docker compose 프로젝트가 다르므로 STACK=prod 로 돌린다(docker stats·DB 샘플은 서버 안에서).
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RUN="${RUN:-run-$(date +%Y%m%d-%H%M%S)}"
DIR="$ROOT/loadtest/out/$RUN"
PROM_URL="${PROM_URL:-http://127.0.0.1:8081/actuator/prometheus}"
INTERVAL="${INTERVAL:-10}"
STACK="${STACK:-local}"
mkdir -p "$DIR"
START=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "$START" > "$DIR/started-at.txt"

if [[ "$STACK" == "local" ]]; then
  export LOADTEST_ENV_FILE="$ROOT/loadtest/.env.local"
  compose() { docker compose -p ohjumwhat-loadtest --env-file "$LOADTEST_ENV_FILE" -f "$ROOT/deploy/docker-compose.yml" -f "$ROOT/loadtest/docker-compose.limits.yml" "$@"; }
else
  compose() { docker compose "$@"; }
fi
db_env() { grep -E "^$1=" "${LOADTEST_ENV_FILE:-$HOME/ohjumwhat/.env}" | cut -d= -f2-; }
psql_db() { compose exec -T db psql -U "$(db_env DB_USERNAME)" -d "$(db_env DB_NAME)" "$@"; }

finish() {
  echo "수집을 마칩니다: $DIR"
  psql_db -c "select calls, round(mean_exec_time::numeric,2) as mean_ms, round(total_exec_time::numeric) as total_ms, rows, left(regexp_replace(query, '\s+', ' ', 'g'), 160) as query from pg_stat_statements where query not like '%pg_stat%' order by total_exec_time desc limit 25" > "$DIR/pg-top.txt" 2>&1
  psql_db -c "select n_tup_upd, n_dead_tup, last_autovacuum from pg_stat_user_tables where relname = 'spring_session'" > "$DIR/spring-session-stats.txt" 2>&1
  compose logs --no-color --since "$START" db 2>/dev/null | grep -E "duration:|lock|deadlock" > "$DIR/slow.log"
  exit 0
}
trap finish INT TERM

echo "수집 시작: $DIR (${INTERVAL}초마다)"
while true; do
  now=$(date +%s)
  curl -s "$PROM_URL" > "$DIR/prom-$now.txt" 2>/dev/null || echo "actuator 응답 없음 $now" >> "$DIR/errors.log"
  docker stats --no-stream --format '{"t":'"$now"',"name":"{{.Name}}","cpu":"{{.CPUPerc}}","mem":"{{.MemUsage}}","net":"{{.NetIO}}"}' >> "$DIR/docker-stats.jsonl" 2>/dev/null
  psql_db -Atc "select $now, wait_event_type, wait_event, state, count(*), left(regexp_replace(min(query), '\s+', ' ', 'g'), 80) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid() and state <> 'idle' group by 2,3,4" >> "$DIR/activity.log" 2>/dev/null
  sleep "$INTERVAL"
done
